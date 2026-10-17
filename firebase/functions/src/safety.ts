import { onCall, HttpsError } from "firebase-functions/https";
import { onDocumentCreated } from "firebase-functions/firestore";
import { logger } from "firebase-functions";
import { Timestamp } from "firebase-admin/firestore";
import { LETTER_HMAC_KEY, LIMITS, OPENAI_API_KEY, REGION } from "./config";
import { callableOpts, loadUser, requireUid } from "./common";
import { col, daysFromNow, db } from "./db";
import { applyAction, resolveReport } from "./enforcement";
import { getClassifier } from "./moderation/classifier";
import { decide } from "./moderation/decision";
import { analyzeText } from "./moderation/rules";
import { verifyLetter } from "./signing";
import type { ReportDoc, ReportedMessage, ThreadDoc } from "./types";
import { requireId, requireReason } from "./validation";

async function participantThread(uid: string, threadId: string) {
  const ref = col.threads().doc(threadId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "thread_not_found");
  const t = snap.data() as ThreadDoc;
  if (!t.participants.includes(uid)) throw new HttpsError("permission-denied", "not_a_participant");
  const other = t.participants[0] === uid ? t.participants[1] : t.participants[0];
  return { ref, thread: t, other };
}

async function block(uid: string, other: string, threadId: string) {
  const pending = await col.deliveries().where("to", "==", uid).where("threadId", "==", threadId).get();
  const batch = db.batch();
  batch.set(col.blocks(uid).doc(other), { blockedUid: other, threadId, at: Timestamp.now() });
  batch.update(col.threads().doc(threadId), { closed: true });
  pending.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
}

/** Blocks the other participant of a thread and closes it for both sides. */
export const blockThread = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const threadId = requireId(req.data?.threadId, "thread");
  const { other } = await participantThread(uid, threadId);
  await block(uid, other, threadId);
  return { ok: true };
});

/**
 * The reporter's device uploads the reported letters from its local database. Signatures prove each
 * letter is unaltered; unverifiable letters are kept for context but never used as evidence.
 */
export const submitReport = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  await loadUser(uid);
  const threadId = requireId(req.data?.threadId, "thread");
  const reason = requireReason(req.data?.reason);
  const note = typeof req.data?.note === "string" ? req.data.note.slice(0, 500) : "";
  const rawMessages: unknown[] = Array.isArray(req.data?.messages) ? req.data.messages : [];
  if (rawMessages.length === 0) throw new HttpsError("invalid-argument", "messages_required");

  const { other } = await participantThread(uid, threadId);

  const dup = await col.reports().where("reporterUid", "==", uid).where("threadId", "==", threadId).limit(1).get();
  if (!dup.empty) {
    await block(uid, other, threadId);
    return { ok: true, reportId: dup.docs[0].id, duplicate: true };
  }
  const since = Timestamp.fromMillis(Date.now() - 86_400_000);
  const recent = await col.reports().where("reporterUid", "==", uid).where("createdAt", ">=", since).count().get();
  if (recent.data().count >= 10) throw new HttpsError("resource-exhausted", "report_limit");

  const key = LETTER_HMAC_KEY.value();
  const messages: ReportedMessage[] = rawMessages.slice(-LIMITS.reportMaxMessages).map((m) => {
    const o = (m ?? {}) as Record<string, unknown>;
    const direction = o.direction === "sent" ? "sent" : "received";
    const msg = {
      deliveryId: String(o.deliveryId ?? "").slice(0, 64),
      body: String(o.body ?? "").slice(0, LIMITS.bodyMaxChars),
      sentAt: Number(o.sentAt ?? 0),
      sig: String(o.sig ?? "").slice(0, 128),
      direction,
    } as const;
    const verified = verifyLetter(
      key,
      {
        deliveryId: msg.deliveryId,
        threadId,
        fromUid: direction === "received" ? other : uid,
        sentAt: msg.sentAt,
        body: msg.body,
      },
      msg.sig,
    );
    return { ...msg, verified };
  });

  const report: ReportDoc = {
    reporterUid: uid,
    reportedUid: other,
    threadId,
    reason,
    note,
    messages,
    status: "pending",
    createdAt: Timestamp.now(),
  };
  const ref = await col.reports().add(report);
  // Reporting also blocks, so the reporter never hears from this user again.
  await block(uid, other, threadId);
  return { ok: true, reportId: ref.id };
});

/** The moderation bot: rules → free AI classifier → decision → enforcement. */
export const onReportCreated = onDocumentCreated(
  { region: REGION, document: "reports/{reportId}", secrets: [OPENAI_API_KEY], timeoutSeconds: 60 },
  async (event) => {
    const reportId = event.params.reportId;
    const report = event.data?.data() as ReportDoc | undefined;
    if (!report) return;

    const evidence = report.messages.filter((m) => m.direction === "received" && m.verified).map((m) => m.body);
    const rules = analyzeText(evidence.join("\n"));

    const key = OPENAI_API_KEY.value();
    const classifier = getClassifier(key.startsWith("sk-") ? key : "");
    const ai = evidence.length ? await classifier.classify(evidence) : null;

    const since = Timestamp.fromMillis(Date.now() - 30 * 86_400_000);
    const recent = await col
      .reports()
      .where("reportedUid", "==", report.reportedUid)
      .where("createdAt", ">=", since)
      .select("reporterUid")
      .get();
    const distinctReporters30d = new Set(recent.docs.map((d) => d.get("reporterUid") as string)).size;

    const reported = await col.users().doc(report.reportedUid).get();
    const priorStrikes = (reported.get("strikes") as number | undefined) ?? 0;

    const decision = decide({
      reason: report.reason,
      ai,
      rules,
      distinctReporters30d,
      priorStrikes,
      hasVerifiedEvidence: evidence.length > 0,
    });
    logger.info("report decided", { reportId, verdict: decision.verdict, why: decision.why });

    await resolveReport(reportId, decision.verdict, {
      analysis: { classifier: classifier.name, ai, rules, distinctReporters30d, priorStrikes, ...decision },
      severity: decision.severity,
    });
    if (decision.verdict !== "dismiss" && reported.exists) {
      await applyAction({
        uid: report.reportedUid,
        verdict: decision.verdict,
        suspendDays: decision.suspendDays,
        suspendNow: decision.suspendNow,
        addStrike: decision.addStrike,
        actor: "bot",
        reportId,
        note: decision.why.join("; "),
      });
    }
  },
);

/** Users can appeal a suspension or ban; appeals land in the support inbox (tools/support-inbox.ts). */
export const submitAppeal = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const message = typeof req.data?.message === "string" ? req.data.message.trim().slice(0, 1000) : "";
  if (message.length < 5) throw new HttpsError("invalid-argument", "message_required");
  const open = await col.appeals().where("uid", "==", uid).where("status", "==", "open").limit(1).get();
  if (!open.empty) throw new HttpsError("already-exists", "appeal_pending");
  const user = await loadUser(uid);
  await col.appeals().add({
    uid,
    kind: typeof req.data?.kind === "string" ? req.data.kind.slice(0, 20) : "appeal",
    message,
    lang: user.lang,
    userStatus: user.status,
    status: "open",
    createdAt: Timestamp.now(),
    expireAt: daysFromNow(365),
  });
  return { ok: true };
});
