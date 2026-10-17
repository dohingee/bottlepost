import { createHash } from "node:crypto";
import { onCall, HttpsError } from "firebase-functions/https";
import { onDocumentDeleted } from "firebase-functions/firestore";
import { logger } from "firebase-functions";
import { FieldValue, Timestamp, type Query } from "firebase-admin/firestore";
import { isPaused } from "./billing";
import { LETTER_HMAC_KEY, LIMITS, REGION } from "./config";
import { blockedSet, callableOpts, loadActiveUser, requireUid } from "./common";
import { col, dayKey, daysFromNow, db, messaging } from "./db";
import { isEligible, pairKey, pickRecipient, type Candidate, type MatchRequest } from "./matching";
import { preSendViolation } from "./moderation/rules";
import { signLetter } from "./signing";
import type { DeliveryDoc, ThreadDoc, UserDoc } from "./types";
import { requireBody, requireGenderPref, requireId } from "./validation";

function bodyHash(body: string): string {
  return createHash("sha256").update(body.toLowerCase().replace(/\s+/g, "")).digest("hex").slice(0, 32);
}

function checkContent(body: string): void {
  const violation = preSendViolation(body);
  if (violation) throw new HttpsError("invalid-argument", violation);
}

/** Which of these candidates already have a thread with uid. Reads only the matching threads. */
async function existingPartners(uid: string, candidates: string[]): Promise<Set<string>> {
  if (candidates.length === 0) return new Set();
  const keys = candidates.map((c) => pairKey(uid, c));
  const snap = await col.threads().where("pairKey", "in", keys).select("participants").get();
  const set = new Set<string>();
  for (const d of snap.docs) for (const p of d.get("participants") as string[]) if (p !== uid) set.add(p);
  return set;
}

async function loadCandidates(base: Query, r: number): Promise<Candidate[]> {
  const toCandidate = (d: FirebaseFirestore.QueryDocumentSnapshot): Candidate => {
    const u = d.data() as UserDoc;
    return {
      uid: d.id,
      gender: u.gender,
      receiveFrom: u.receiveFrom ?? "ANY",
      status: u.status,
      pendingInbox: u.pendingInbox ?? 0,
      lastActiveMs: u.lastActive?.toMillis() ?? 0,
      lang: u.lang,
      fcmToken: u.fcmToken,
    };
  };
  const n = LIMITS.matchCandidatePool;
  const after = await base.where("rand", ">=", r).orderBy("rand").limit(n).get();
  let docs = after.docs;
  if (docs.length < n) {
    // Wrap around the random ring.
    const before = await base.where("rand", "<", r).orderBy("rand").limit(n - docs.length).get();
    docs = docs.concat(before.docs);
  }
  return docs.map(toCandidate);
}

/** Pass knownToken when the recipient doc was already read, to save a billed read. */
async function notify(toUid: string, kind: DeliveryDoc["kind"], knownToken?: string | null): Promise<void> {
  const token =
    knownToken !== undefined ? knownToken : ((await col.users().doc(toUid).get()).get("fcmToken") as string | undefined);
  if (!token) return;
  try {
    // Data-only: the app builds a localized, content-free notification ("You've got a letter").
    await messaging.send({ token, data: { type: "letter", kind }, android: { priority: "high" } });
  } catch (err) {
    logger.info("push failed", { code: (err as { code?: string }).code });
    if ((err as { code?: string }).code === "messaging/registration-token-not-registered") {
      await col.users().doc(toUid).update({ fcmToken: FieldValue.delete() });
    }
  }
}

function newDelivery(params: {
  to: string;
  fromUid: string;
  threadId: string;
  kind: DeliveryDoc["kind"];
  body: string;
  sender: UserDoc;
}) {
  const ref = col.deliveries().doc();
  const sentAt = Date.now();
  const sig = signLetter(LETTER_HMAC_KEY.value(), {
    deliveryId: ref.id,
    threadId: params.threadId,
    fromUid: params.fromUid,
    sentAt,
    body: params.body,
  });
  const doc: DeliveryDoc = {
    to: params.to,
    threadId: params.threadId,
    kind: params.kind,
    body: params.body,
    lang: params.sender.lang,
    senderNickname: params.sender.nickname,
    senderGender: params.sender.gender,
    sentAt,
    sig,
    expireAt: daysFromNow(LIMITS.deliveryTtlDays),
  };
  return { ref, doc };
}

/** First letter to a random stranger of the chosen gender. */
export const sendLetter = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const sender = await loadActiveUser(uid);
  // Spending guard (billing.ts): only new first letters stop; conversations keep going.
  if (await isPaused()) throw new HttpsError("unavailable", "service_paused");
  const body = requireBody(req.data?.body);
  const wantGender = requireGenderPref(req.data?.wantGender);
  const sameLanguageOnly = req.data?.sameLanguageOnly === true;
  checkContent(body);

  const today = dayKey();
  const sentToday = sender.sentDay === today ? sender.sentToday : 0;
  const dailyLimit = sender.linked ? LIMITS.dailyFirstLettersLinked : LIMITS.dailyFirstLettersAnonymous;
  if (sentToday >= dailyLimit) throw new HttpsError("resource-exhausted", "daily_limit");
  const hash = bodyHash(body);
  if ((sender as UserDoc & { lastBodyHash?: string }).lastBodyHash === hash) {
    throw new HttpsError("invalid-argument", "duplicate_letter");
  }

  let base: Query = col.users().where("status", "==", "active");
  if (wantGender !== "ANY") base = base.where("gender", "==", wantGender);
  if (sameLanguageOnly) base = base.where("lang", "==", sender.lang);

  const matchReq: MatchRequest = {
    senderUid: uid,
    senderGender: sender.gender,
    senderLang: sender.lang,
    wantGender,
    sameLanguageOnly,
    excluded: await blockedSet(uid),
    existingPartners: new Set(),
    nowMs: Date.now(),
  };
  // Small random samples instead of one big one: most sends succeed on the first 10 reads.
  let recipient: Candidate | null = null;
  for (let attempt = 0; attempt < LIMITS.matchAttempts && !recipient; attempt++) {
    const eligible = (await loadCandidates(base, Math.random())).filter((c) => isEligible(c, matchReq));
    const partners = await existingPartners(uid, eligible.map((c) => c.uid));
    recipient = pickRecipient(eligible, { ...matchReq, existingPartners: partners });
  }
  if (!recipient) throw new HttpsError("unavailable", "no_recipient_available");

  const threadRef = col.threads().doc();
  const { ref: deliveryRef, doc: delivery } = newDelivery({
    to: recipient.uid,
    fromUid: uid,
    threadId: threadRef.id,
    kind: "first",
    body,
    sender,
  });
  const now = Timestamp.now();
  const thread: ThreadDoc = {
    participants: [uid, recipient.uid],
    pairKey: pairKey(uid, recipient.uid),
    createdAt: now,
    lastAt: now,
    replied: false,
    closed: false,
    replyDay: today,
    replyCounts: {},
    lastFrom: uid,
    leftBy: null,
  };

  const batch = db.batch();
  batch.set(threadRef, thread);
  batch.set(deliveryRef, delivery);
  batch.update(col.users().doc(uid), {
    sentDay: today,
    sentToday: sentToday + 1,
    lastBodyHash: hash,
    lastActive: now,
  });
  batch.update(col.users().doc(recipient.uid), { pendingInbox: FieldValue.increment(1) });
  await batch.commit();
  await notify(recipient.uid, "first", recipient.fcmToken ?? null);

  return {
    threadId: threadRef.id,
    deliveryId: deliveryRef.id,
    sentAt: delivery.sentAt,
    sig: delivery.sig,
    remainingToday: dailyLimit - sentToday - 1,
  };
});

/** Reply inside an existing thread. The client only knows the threadId, never the other uid. */
export const replyLetter = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const sender = await loadActiveUser(uid);
  const threadId = requireId(req.data?.threadId, "thread");
  const body = requireBody(req.data?.body);
  checkContent(body);

  const threadRef = col.threads().doc(threadId);
  const today = dayKey();
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(threadRef);
    if (!snap.exists) throw new HttpsError("not-found", "thread_not_found");
    const t = snap.data() as ThreadDoc;
    if (!t.participants.includes(uid)) throw new HttpsError("permission-denied", "not_a_participant");
    if (t.closed) throw new HttpsError("failed-precondition", "thread_closed");
    // Letters alternate, like real mail: wait for the other side before writing again.
    if (t.lastFrom === uid) throw new HttpsError("failed-precondition", "wait_for_reply");
    const other = t.participants[0] === uid ? t.participants[1] : t.participants[0];

    const counts = t.replyDay === today ? { ...t.replyCounts } : {};
    if ((counts[uid] ?? 0) >= LIMITS.dailyRepliesPerThread) throw new HttpsError("resource-exhausted", "daily_limit");
    counts[uid] = (counts[uid] ?? 0) + 1;

    const { ref, doc } = newDelivery({ to: other, fromUid: uid, threadId, kind: "reply", body, sender });
    tx.set(ref, doc);
    tx.update(threadRef, {
      lastAt: Timestamp.now(),
      replied: t.replied || uid === t.participants[1],
      replyDay: today,
      replyCounts: counts,
      lastFrom: uid,
    });
    tx.update(col.users().doc(other), { pendingInbox: FieldValue.increment(1) });
    // lastActive only matters for matching, at hour granularity; skip the write when fresh.
    if (Date.now() - (sender.lastActive?.toMillis() ?? 0) > LIMITS.activeTouchMinutes * 60_000) {
      tx.update(col.users().doc(uid), { lastActive: Timestamp.now() });
    }
    return { other, deliveryId: ref.id, sentAt: doc.sentAt, sig: doc.sig };
  });

  // A block closes the thread (see safety.ts), so reaching here means neither side has blocked.
  await notify(result.other, "reply");
  return { threadId, deliveryId: result.deliveryId, sentAt: result.sentAt, sig: result.sig };
});

/**
 * Deliveries are deleted by the recipient device right after it stores the letter locally
 * (allowed by security rules), or by the TTL policy after 7 days. Keep the inbox counter in sync.
 */
export const onDeliveryDeleted = onDocumentDeleted({ region: REGION, document: "deliveries/{id}" }, async (event) => {
  const to = event.data?.get("to") as string | undefined;
  // "left" notices never counted towards the inbox.
  if (!to || event.data?.get("kind") === "left") return;
  // Blind decrement (no read). A rare drift below zero is harmless: matching only checks the upper limit.
  await col.users().doc(to).update({ pendingInbox: FieldValue.increment(-1) }).catch(() => undefined);
});

/**
 * Leave a conversation. The thread closes for both sides and the other participant gets a
 * content-free "left" notice so their app can show that the conversation ended. Leaving a thread
 * the other side already left just confirms it (used when the remaining person deletes it).
 */
export const leaveThread = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const threadId = requireId(req.data?.threadId, "thread");
  const threadRef = col.threads().doc(threadId);

  const other = await db.runTransaction(async (tx) => {
    const snap = await tx.get(threadRef);
    if (!snap.exists) return null;
    const t = snap.data() as ThreadDoc;
    if (!t.participants.includes(uid)) throw new HttpsError("permission-denied", "not_a_participant");
    if (t.leftBy) return null;
    const partner = t.participants[0] === uid ? t.participants[1] : t.participants[0];
    tx.update(threadRef, { leftBy: uid, closed: true, lastAt: Timestamp.now() });
    const notice: Pick<DeliveryDoc, "to" | "threadId" | "kind" | "sentAt" | "expireAt"> = {
      to: partner,
      threadId,
      kind: "left",
      sentAt: Date.now(),
      expireAt: daysFromNow(LIMITS.deliveryTtlDays),
    };
    tx.set(col.deliveries().doc(), notice);
    return partner;
  });

  // Letters still waiting for the leaver in this thread will never be read.
  const pending = await col.deliveries().where("to", "==", uid).where("threadId", "==", threadId).get();
  await Promise.all(pending.docs.map((d) => d.ref.delete()));
  if (other) await notify(other, "left");
  return { ok: true };
});
