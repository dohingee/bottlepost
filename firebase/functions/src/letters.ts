import { createHash } from "node:crypto";
import { onCall, HttpsError } from "firebase-functions/https";
import { onDocumentDeleted } from "firebase-functions/firestore";
import { logger } from "firebase-functions";
import { FieldValue, Timestamp, type Query } from "firebase-admin/firestore";
import { LETTER_HMAC_KEY, LIMITS, REGION } from "./config";
import { blockedSet, callableOpts, loadActiveUser, requireUid } from "./common";
import { col, dayKey, daysFromNow, db, messaging } from "./db";
import { pickRecipient, type Candidate, type MatchRequest } from "./matching";
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

async function threadPartners(uid: string): Promise<Set<string>> {
  const snap = await col.threads().where("participants", "array-contains", uid).select("participants").limit(1000).get();
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

async function notify(toUid: string, kind: DeliveryDoc["kind"]): Promise<void> {
  const token = (await col.users().doc(toUid).get()).get("fcmToken") as string | undefined;
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

  const [excluded, existingPartners, candidates] = await Promise.all([
    blockedSet(uid),
    threadPartners(uid),
    loadCandidates(base, Math.random()),
  ]);
  const matchReq: MatchRequest = {
    senderUid: uid,
    senderGender: sender.gender,
    senderLang: sender.lang,
    wantGender,
    sameLanguageOnly,
    excluded,
    existingPartners,
    nowMs: Date.now(),
  };
  const recipient = pickRecipient(candidates, matchReq);
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
    createdAt: now,
    lastAt: now,
    replied: false,
    closed: false,
    replyDay: today,
    replyCounts: {},
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
  await notify(recipient.uid, "first");

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
    });
    tx.update(col.users().doc(other), { pendingInbox: FieldValue.increment(1) });
    tx.update(col.users().doc(uid), { lastActive: Timestamp.now() });
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
  if (!to) return;
  const userRef = col.users().doc(to);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    if (!snap.exists) return;
    const pending = Math.max(0, ((snap.get("pendingInbox") as number | undefined) ?? 1) - 1);
    tx.update(userRef, { pendingInbox: pending });
  });
});
