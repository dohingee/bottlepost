import { auth, col, db } from "./db";

/**
 * Deletes everything the server holds about a user (Play account deletion requirement).
 * Ban records keyed by e-mail hash are intentionally kept (disclosed in the privacy policy).
 */
export async function deleteUserData(uid: string): Promise<void> {
  const deliveries = await col.deliveries().where("to", "==", uid).get();
  const threads = await col.threads().where("participants", "array-contains", uid).get();
  const userRef = col.users().doc(uid);

  const refs = [
    ...deliveries.docs.map((d) => d.ref),
    ...(await col.blocks(uid).listDocuments()),
    ...(await col.userPrivate(uid).listDocuments()),
    ...(await userRef.collection("moderationLog").listDocuments()),
  ];
  const writer = db.bulkWriter();
  refs.forEach((r) => writer.delete(r));
  // Threads are closed, not deleted, so the other side sees the conversation ended.
  threads.docs.forEach((t) => writer.update(t.ref, { closed: true }));
  writer.delete(userRef);
  await writer.close();

  try {
    await auth.deleteUser(uid);
  } catch (err) {
    console.info("auth user already gone", { uid, err: String(err) });
  }
}
