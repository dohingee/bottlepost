import { Timestamp } from "firebase-admin/firestore";
import { LIMITS } from "./config";
import { auth, col, daysFromNow, db } from "./db";
import { notify } from "./push";
import type { DeliveryDoc, ThreadDoc } from "./types";

/** Stands in for a deleted user's uid in threads the other person still has. */
const DELETED = "deleted";

/**
 * Deletes everything the server holds about a user (Play account deletion requirement).
 * Ban records keyed by e-mail hash are intentionally kept, and reports keep their 90-day retention
 * (both disclosed in the privacy policy).
 */
export async function deleteUserData(uid: string): Promise<void> {
  const [deliveries, threads, blockedBy, reportsBy, reportsAgainst] = await Promise.all([
    col.deliveries().where("to", "==", uid).get(),
    col.threads().where("participants", "array-contains", uid).get(),
    db.collectionGroup("blocks").where("blockedUid", "==", uid).get(),
    col.reports().where("reporterUid", "==", uid).get(),
    col.reports().where("reportedUid", "==", uid).get(),
  ]);
  const userRef = col.users().doc(uid);

  const refs = [
    ...deliveries.docs.map((d) => d.ref),
    ...blockedBy.docs.map((d) => d.ref),
    ...(await col.blocks(uid).listDocuments()),
    ...(await col.userPrivate(uid).listDocuments()),
    ...(await userRef.collection("moderationLog").listDocuments()),
  ];
  const writer = db.bulkWriter();
  refs.forEach((r) => writer.delete(r));

  // Threads stay for the other person (closed, uid replaced). Whoever was still talking gets the
  // same "left" notice as when someone leaves, so nobody keeps waiting for a reply.
  const tellLeft: string[] = [];
  for (const t of threads.docs) {
    const thread = t.data() as ThreadDoc;
    const other = thread.participants.find((p) => p !== uid);
    if (other && other !== DELETED && !thread.closed && !thread.leftBy) {
      const notice: Pick<DeliveryDoc, "to" | "threadId" | "kind" | "sentAt" | "expireAt"> = {
        to: other,
        threadId: t.id,
        kind: "left",
        sentAt: Date.now(),
        expireAt: daysFromNow(LIMITS.deliveryTtlDays),
      };
      writer.create(col.deliveries().doc(), notice);
      tellLeft.push(other);
    }
    writer.update(t.ref, {
      closed: true,
      leftBy: thread.leftBy ?? DELETED,
      participants: thread.participants.map((p) => (p === uid ? DELETED : p)),
      pairKey: DELETED,
      lastAt: Timestamp.now(),
    });
  }

  // Open reports have no expiry until resolved; give them the normal retention so they cannot outlive it.
  for (const r of [...reportsBy.docs, ...reportsAgainst.docs]) {
    if (!r.get("expireAt")) writer.update(r.ref, { expireAt: daysFromNow(LIMITS.reportRetentionDays) });
  }
  writer.delete(userRef);
  await writer.close();
  await Promise.all(tellLeft.map((other) => notify(other, "left")));

  try {
    await auth.deleteUser(uid);
  } catch (err) {
    console.info("auth user already gone", { uid, err: String(err) });
  }
}
