import { defineSecret } from "firebase-functions/params";

/** Seoul region; change if most users are elsewhere. */
export const REGION = "asia-northeast3";

/** HMAC key that signs every letter so reports can prove a letter is genuine without the server keeping it. */
export const LETTER_HMAC_KEY = defineSecret("LETTER_HMAC_KEY");

/**
 * Optional AI classifier key. Set the secret to "disabled" to run without it: reports then use
 * rules + human review only.
 */
export const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");

/** App Check is enforced everywhere except the local Functions emulator (no Play Integrity there). */
export const ENFORCE_APP_CHECK = process.env.FUNCTIONS_EMULATOR !== "true";

export const LIMITS = {
  minAge: 18,
  bodyMaxChars: 1000,
  bodyMinChars: 2,
  nicknameMaxChars: 20,
  maxTags: 3,
  dailyFirstLettersAnonymous: 3,
  dailyFirstLettersLinked: 10,
  dailyRepliesPerThread: 30,
  /** Recipients with this many undelivered letters are skipped by matching. */
  maxPendingInbox: 20,
  /** Only users active within this window receive new letters. */
  activeWindowDays: 7,
  deliveryTtlDays: 7,
  reportRetentionDays: 90,
  reportMaxMessages: 6,
  /** Users read per matching attempt. Each one is a billed Firestore read, so keep it small. */
  matchCandidatePool: 10,
  matchAttempts: 2,
  /** lastActive is written at most this often (writes cost 3x reads). */
  activeTouchMinutes: 60,
} as const;

export const GENDERS = ["M", "F"] as const;
export type Gender = (typeof GENDERS)[number];
export type GenderPref = Gender | "ANY";

export const SUPPORTED_LANGS = ["ko", "en", "ja", "es", "pt", "id", "vi", "th"] as const;

export const REPORT_REASONS = ["spam", "scam", "sexual", "harassment", "minor", "hate", "self_harm", "other"] as const;
export type ReportReason = (typeof REPORT_REASONS)[number];
