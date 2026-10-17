import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { getMessaging } from "firebase-admin/messaging";

/** Re-exported so tools/ can use it without its own firebase-admin install. */
export { Timestamp };

if (getApps().length === 0) initializeApp();

export const db = getFirestore();
export const auth = getAuth();
export const messaging = getMessaging();

export const col = {
  users: () => db.collection("users"),
  /** users/{uid}/private/backup holds the backup encryption key; never readable by clients. */
  userPrivate: (uid: string) => db.collection("users").doc(uid).collection("private"),
  blocks: (uid: string) => db.collection("users").doc(uid).collection("blocks"),
  threads: () => db.collection("threads"),
  deliveries: () => db.collection("deliveries"),
  reports: () => db.collection("reports"),
  bans: () => db.collection("bans"),
  appeals: () => db.collection("appeals"),
};

export function daysFromNow(days: number): Timestamp {
  return Timestamp.fromMillis(Date.now() + days * 86_400_000);
}

/** UTC day key used for daily counters, e.g. "2026-10-17". */
export function dayKey(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}
