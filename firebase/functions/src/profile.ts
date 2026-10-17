import { randomBytes } from "node:crypto";
import { onCall, HttpsError } from "firebase-functions/https";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { callableOpts, loadUser, requireUid } from "./common";
import { col, dayKey } from "./db";
import { emailHash } from "./hash";
import type { UserDoc } from "./types";
import {
  cleanNickname,
  cleanTags,
  requireAdultBirthYear,
  requireGender,
  requireGenderPref,
  requireLang,
} from "./validation";

/** Public part of the profile returned to the owner's device. */
function view(u: UserDoc) {
  return {
    gender: u.gender,
    birthYear: u.birthYear,
    lang: u.lang,
    country: u.country,
    receiveFrom: u.receiveFrom,
    nickname: u.nickname,
    tags: u.tags,
    linked: u.linked,
    status: u.status,
    suspendedUntil: u.suspendedUntil?.toMillis() ?? null,
  };
}

/** Called once from onboarding. Gender and birth year cannot be changed later (prevents pool abuse). */
export const createProfile = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const d = req.data ?? {};
  if (d.acceptedTerms !== true) throw new HttpsError("failed-precondition", "terms_not_accepted");

  const ref = col.users().doc(uid);
  const existing = await ref.get();
  if (existing.exists) return view(existing.data() as UserDoc);

  const now = Timestamp.now();
  const user: UserDoc = {
    gender: requireGender(d.gender),
    birthYear: requireAdultBirthYear(d.birthYear),
    lang: requireLang(d.lang),
    country: typeof d.country === "string" ? d.country.slice(0, 2).toUpperCase() : "",
    receiveFrom: d.receiveFrom == null ? "ANY" : requireGenderPref(d.receiveFrom),
    nickname: cleanNickname(d.nickname),
    tags: cleanTags(d.tags),
    linked: false,
    status: "active",
    suspendedUntil: null,
    strikes: 0,
    rand: Math.random(),
    pendingInbox: 0,
    sentDay: dayKey(),
    sentToday: 0,
    lastActive: now,
    createdAt: now,
  };
  await ref.set({ ...user, termsAcceptedAt: now, termsVersion: String(d.termsVersion ?? "1") });
  return view(user);
});

export const getProfile = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const snap = await col.users().doc(uid).get();
  if (!snap.exists) return null;
  await snap.ref.update({ lastActive: Timestamp.now() });
  return view(snap.data() as UserDoc);
});

export const updateProfile = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const d = req.data ?? {};
  const patch: Partial<UserDoc> = {};
  if ("nickname" in d) patch.nickname = cleanNickname(d.nickname);
  if ("tags" in d) patch.tags = cleanTags(d.tags);
  if ("lang" in d) patch.lang = requireLang(d.lang);
  if ("receiveFrom" in d) patch.receiveFrom = requireGenderPref(d.receiveFrom);
  await loadUser(uid);
  await col.users().doc(uid).update({ ...patch, lastActive: Timestamp.now() });
  return view(await loadUser(uid));
});

export const registerPushToken = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const token = req.data?.token;
  if (typeof token !== "string" || token.length > 4096) throw new HttpsError("invalid-argument", "invalid_token");
  await col.users().doc(uid).update({ fcmToken: token, lastActive: Timestamp.now() });
  return { ok: true };
});

/**
 * Called after the device links Google to its anonymous Firebase user (linkWithCredential keeps the uid).
 * Records the link and enforces ban evasion protection via a hash of the Google e-mail.
 */
export const confirmGoogleLink = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const token = req.auth!.token;
  if (token.firebase?.sign_in_provider !== "google.com" && !token.firebase?.identities?.["google.com"]) {
    throw new HttpsError("failed-precondition", "google_not_linked");
  }
  const email = token.email;
  if (!email || token.email_verified !== true) throw new HttpsError("failed-precondition", "email_unverified");

  const hash = emailHash(email);
  const ban = await col.bans().doc(hash).get();
  if (ban.exists) {
    await col.users().doc(uid).set({ status: "banned", linked: true, emailHash: hash }, { merge: true });
    throw new HttpsError("permission-denied", "account_banned");
  }
  await col.users().doc(uid).update({ linked: true, emailHash: hash, lastActive: Timestamp.now() });
  return view(await loadUser(uid));
});

/**
 * Per-user AES-256 key for the Drive backup. Kept server-side so that neither Google Drive alone
 * nor our server alone can read a user's conversations. Only for Google-linked accounts, which is
 * also what makes the key recoverable after reinstalling.
 */
export const getBackupKey = onCall(callableOpts, async (req) => {
  const uid = requireUid(req);
  const user = await loadUser(uid);
  if (!user.linked) throw new HttpsError("failed-precondition", "google_link_required");
  const ref = col.userPrivate(uid).doc("backup");
  const key = await col.users().firestore.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (snap.exists) return snap.get("key") as string;
    const fresh = randomBytes(32).toString("base64");
    tx.set(ref, { key: fresh, createdAt: FieldValue.serverTimestamp() });
    return fresh;
  });
  return { key };
});
