import { HttpsError, type CallableOptions, type CallableRequest } from "firebase-functions/https";
import { Timestamp } from "firebase-admin/firestore";
import { ENFORCE_APP_CHECK, REGION } from "./config";
import { LETTER_HMAC_KEY } from "./secrets";
import { col } from "./db";
import type { UserDoc } from "./types";

export const callableOpts: CallableOptions = {
  region: REGION,
  enforceAppCheck: ENFORCE_APP_CHECK,
  secrets: [LETTER_HMAC_KEY],
};

export function requireUid(req: CallableRequest): string {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "sign_in_required");
  return uid;
}

export function requireAdmin(req: CallableRequest): string {
  const uid = requireUid(req);
  if (req.auth?.token.admin !== true) throw new HttpsError("permission-denied", "admin_only");
  return uid;
}

export async function loadUser(uid: string): Promise<UserDoc> {
  const snap = await col.users().doc(uid).get();
  if (!snap.exists) throw new HttpsError("failed-precondition", "profile_required");
  return snap.data() as UserDoc;
}

/** Loads the user and rejects suspended/banned accounts (lifting expired suspensions on the way). */
export async function loadActiveUser(uid: string): Promise<UserDoc> {
  const user = await loadUser(uid);
  if (user.status === "banned") throw new HttpsError("permission-denied", "account_banned");
  if (user.status === "suspended") {
    const until = user.suspendedUntil?.toMillis() ?? Infinity;
    if (until > Date.now()) throw new HttpsError("permission-denied", "account_suspended");
    await col.users().doc(uid).update({ status: "active", suspendedUntil: null });
    user.status = "active";
  }
  return user;
}

export async function touch(uid: string): Promise<void> {
  await col.users().doc(uid).update({ lastActive: Timestamp.now() });
}

/** Both directions: users this uid blocked, and users who blocked this uid. */
export async function blockedSet(uid: string): Promise<Set<string>> {
  const mine = await col.blocks(uid).select().get();
  const set = new Set(mine.docs.map((d) => d.id));
  const theirs = await col.users().firestore.collectionGroup("blocks").where("blockedUid", "==", uid).select().get();
  for (const d of theirs.docs) {
    const owner = d.ref.parent.parent?.id;
    if (owner) set.add(owner);
  }
  return set;
}
