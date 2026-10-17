import type { Gender, GenderPref } from "./config";
import { LIMITS } from "./config";

/** Same value for (a, b) and (b, a). */
export function pairKey(a: string, b: string): string {
  return a < b ? `${a}|${b}` : `${b}|${a}`;
}

export interface Candidate {
  uid: string;
  fcmToken?: string;
  gender: Gender;
  receiveFrom: GenderPref;
  status: string;
  pendingInbox: number;
  lastActiveMs: number;
  lang: string;
}

export interface MatchRequest {
  senderUid: string;
  senderGender: Gender;
  senderLang: string;
  wantGender: GenderPref;
  sameLanguageOnly: boolean;
  /** UIDs blocked by the sender or who blocked the sender. */
  excluded: Set<string>;
  /** UIDs the sender already has a thread with. */
  existingPartners: Set<string>;
  nowMs: number;
}

/** Pure filter so the matching policy can be unit-tested without Firestore. */
export function isEligible(c: Candidate, req: MatchRequest): boolean {
  if (c.uid === req.senderUid) return false;
  if (c.status !== "active") return false;
  if (req.wantGender !== "ANY" && c.gender !== req.wantGender) return false;
  if (c.receiveFrom !== "ANY" && c.receiveFrom !== req.senderGender) return false;
  if (req.sameLanguageOnly && c.lang !== req.senderLang) return false;
  if (c.pendingInbox >= LIMITS.maxPendingInbox) return false;
  if (req.nowMs - c.lastActiveMs > LIMITS.activeWindowDays * 86_400_000) return false;
  if (req.excluded.has(c.uid)) return false;
  if (req.existingPartners.has(c.uid)) return false;
  return true;
}

/**
 * Picks one recipient. Candidates come from a query ordered by a random field starting at a random
 * point, so the list is already a uniform sample; prefer the least-loaded inbox among the first few
 * so popular profiles don't get flooded.
 */
export function pickRecipient(candidates: Candidate[], req: MatchRequest, random = Math.random): Candidate | null {
  const eligible = candidates.filter((c) => isEligible(c, req));
  if (eligible.length === 0) return null;
  const head = eligible.slice(0, 5);
  const minPending = Math.min(...head.map((c) => c.pendingInbox));
  const lightest = head.filter((c) => c.pendingInbox === minPending);
  return lightest[Math.floor(random() * lightest.length)];
}
