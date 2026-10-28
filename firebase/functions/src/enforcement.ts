import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { LIMITS } from "./config";
import { col, daysFromNow, db } from "./db";
import type { Verdict } from "./types";

/**
 * Single place that changes a user's standing. Shared by the report pipeline, the admin callables
 * and the operator CLI (tools/moderation-cli.ts) so every path leaves the same audit trail.
 * Must not import firebase-functions so the CLI can run it with plain admin credentials.
 */
export interface Action {
  uid: string;
  verdict: Verdict;
  /** 0 with verdict "review" + suspendNow means "suspended until a human decides". */
  suspendDays: number;
  suspendNow: boolean;
  addStrike: boolean;
  actor: string;
  reportId?: string;
  note?: string;
}

export async function applyAction(a: Action): Promise<void> {
  const userRef = col.users().doc(a.uid);
  const snap = await userRef.get();
  if (!snap.exists) return;
  const emailHash = snap.get("emailHash") as string | undefined;

  const patch: Record<string, unknown> = {};
  if (a.addStrike) patch.strikes = FieldValue.increment(1);

  if (a.verdict === "ban_permanent") {
    patch.status = "banned";
    patch.suspendedUntil = null;
  } else if (a.verdict === "suspend" || (a.verdict === "review" && a.suspendNow)) {
    patch.status = "suspended";
    patch.suspendedUntil = a.suspendDays > 0 ? daysFromNow(a.suspendDays) : null;
  }

  const batch = db.batch();
  if (Object.keys(patch).length) batch.update(userRef, patch);
  if (a.verdict === "ban_permanent" && emailHash) {
    batch.set(col.bans().doc(emailHash), {
      uid: a.uid,
      reportId: a.reportId ?? null,
      at: Timestamp.now(),
      // Ban records are kept 3 years (privacy policy), then removed by TTL.
      expireAt: daysFromNow(365 * 3),
    });
  }
  batch.set(userRef.collection("moderationLog").doc(), {
    verdict: a.verdict,
    suspendDays: a.suspendDays,
    addStrike: a.addStrike,
    actor: a.actor,
    reportId: a.reportId ?? null,
    note: a.note ?? "",
    at: Timestamp.now(),
  });
  await batch.commit();
  if (a.verdict === "ban_permanent" || patch.status === "suspended") await deletePendingLetters(a.uid);
}

/**
 * Letters the user sent that were not picked up yet: removed when they are suspended or banned, so
 * reported content stops reaching anyone (App Review 1.2: remove the content, eject the user).
 * Deliveries carry no sender uid, so they are found through the user's threads.
 */
export async function deletePendingLetters(uid: string): Promise<number> {
  const threads = await col.threads().where("participants", "array-contains", uid).get();
  let removed = 0;
  for (const t of threads.docs) {
    const pending = await col.deliveries().where("threadId", "==", t.id).get();
    const theirs = pending.docs.filter((d) => d.get("to") !== uid && d.get("kind") !== "left");
    if (!theirs.length) continue;
    const batch = db.batch();
    theirs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    removed += theirs.length;
  }
  return removed;
}

/** Lifts a suspension or ban (appeal granted). */
export async function restoreUser(uid: string, actor: string, note = ""): Promise<void> {
  const userRef = col.users().doc(uid);
  const snap = await userRef.get();
  if (!snap.exists) return;
  const emailHash = snap.get("emailHash") as string | undefined;
  const batch = db.batch();
  batch.update(userRef, { status: "active", suspendedUntil: null });
  if (emailHash) batch.delete(col.bans().doc(emailHash));
  batch.set(userRef.collection("moderationLog").doc(), { verdict: "restore", actor, note, at: Timestamp.now() });
  await batch.commit();
}

export async function resolveReport(reportId: string, verdict: Verdict, extra: Record<string, unknown> = {}) {
  await col.reports().doc(reportId).update({
    status: verdict === "review" ? "review" : "resolved",
    verdict,
    ...extra,
    ...(verdict === "review"
      ? {}
      : { resolvedAt: Timestamp.now(), expireAt: daysFromNow(LIMITS.reportRetentionDays) }),
  });
}
