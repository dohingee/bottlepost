import type { Timestamp } from "firebase-admin/firestore";
import type { Gender, GenderPref, ReportReason } from "./config";

export type UserStatus = "active" | "suspended" | "banned";

export interface UserDoc {
  gender: Gender;
  birthYear: number;
  lang: string;
  country: string;
  /** Who this user is willing to receive first letters from. */
  receiveFrom: GenderPref;
  nickname: string;
  tags: string[];
  linked: boolean;
  emailHash?: string;
  fcmToken?: string;
  status: UserStatus;
  suspendedUntil?: Timestamp | null;
  strikes: number;
  /** Random value for uniform random matching. */
  rand: number;
  pendingInbox: number;
  sentDay: string;
  sentToday: number;
  lastActive: Timestamp;
  createdAt: Timestamp;
}

export interface ThreadDoc {
  /** [starter, recipient]. Never exposed to clients. */
  participants: [string, string];
  createdAt: Timestamp;
  lastAt: Timestamp;
  replied: boolean;
  closed: boolean;
  /** Per-participant reply counters for the day, keyed by uid. */
  replyDay: string;
  replyCounts: Record<string, number>;
}

/** Transient: lives only until the recipient device fetches and deletes it, or TTL. */
export interface DeliveryDoc {
  to: string;
  threadId: string;
  kind: "first" | "reply";
  body: string;
  lang: string;
  senderNickname: string;
  senderGender: Gender;
  sentAt: number;
  sig: string;
  expireAt: Timestamp;
}

export interface ReportedMessage {
  deliveryId: string;
  body: string;
  sentAt: number;
  sig: string;
  /** "received" = written by the reported user, "sent" = written by the reporter. */
  direction: "received" | "sent";
  verified: boolean;
}

export type Verdict = "ban_permanent" | "suspend" | "review" | "dismiss";

export interface ReportDoc {
  reporterUid: string;
  reportedUid: string;
  threadId: string;
  reason: ReportReason;
  note: string;
  messages: ReportedMessage[];
  status: "pending" | "review" | "resolved";
  verdict?: Verdict;
  analysis?: unknown;
  createdAt: Timestamp;
  resolvedAt?: Timestamp;
  /** Firestore TTL field: set when resolved. */
  expireAt?: Timestamp;
}
