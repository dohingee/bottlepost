import { HttpsError } from "firebase-functions/https";
import { GENDERS, LIMITS, REPORT_REASONS, SUPPORTED_LANGS, type Gender, type GenderPref, type ReportReason } from "./config";

export function ageFromBirthYear(birthYear: number, now = new Date()): number {
  // Birth year only, so assume the birthday has not yet passed this year (the conservative choice).
  return now.getUTCFullYear() - birthYear - 1;
}

export function requireGender(v: unknown): Gender {
  if (typeof v === "string" && (GENDERS as readonly string[]).includes(v)) return v as Gender;
  throw new HttpsError("invalid-argument", "invalid_gender");
}

export function requireGenderPref(v: unknown): GenderPref {
  if (v === "ANY") return "ANY";
  return requireGender(v);
}

export function requireAdultBirthYear(v: unknown, now = new Date()): number {
  const year = Number(v);
  if (!Number.isInteger(year) || year < 1900 || year > now.getUTCFullYear()) {
    throw new HttpsError("invalid-argument", "invalid_birth_year");
  }
  if (ageFromBirthYear(year, now) < LIMITS.minAge) {
    throw new HttpsError("failed-precondition", "underage");
  }
  return year;
}

export function requireLang(v: unknown): string {
  if (typeof v === "string" && (SUPPORTED_LANGS as readonly string[]).includes(v)) return v;
  // Unsupported UI languages still work; letters are translated on-device.
  if (typeof v === "string" && /^[a-z]{2,3}$/.test(v)) return v;
  throw new HttpsError("invalid-argument", "invalid_lang");
}

export function cleanNickname(v: unknown): string {
  if (v == null || v === "") return "";
  if (typeof v !== "string") throw new HttpsError("invalid-argument", "invalid_nickname");
  const s = v.trim().replace(/\s+/g, " ");
  if (s.length > LIMITS.nicknameMaxChars) throw new HttpsError("invalid-argument", "nickname_too_long");
  return s;
}

export function cleanTags(v: unknown): string[] {
  if (v == null) return [];
  if (!Array.isArray(v)) throw new HttpsError("invalid-argument", "invalid_tags");
  return v
    .filter((t): t is string => typeof t === "string")
    .map((t) => t.trim().slice(0, 20))
    .filter(Boolean)
    .slice(0, LIMITS.maxTags);
}

export function requireBody(v: unknown): string {
  if (typeof v !== "string") throw new HttpsError("invalid-argument", "invalid_body");
  const s = v.trim();
  if (s.length < LIMITS.bodyMinChars) throw new HttpsError("invalid-argument", "body_too_short");
  if (s.length > LIMITS.bodyMaxChars) throw new HttpsError("invalid-argument", "body_too_long");
  return s;
}

export function requireId(v: unknown, name: string): string {
  if (typeof v === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(v)) return v;
  throw new HttpsError("invalid-argument", `invalid_${name}`);
}

export function requireReason(v: unknown): ReportReason {
  if (typeof v === "string" && (REPORT_REASONS as readonly string[]).includes(v)) return v as ReportReason;
  throw new HttpsError("invalid-argument", "invalid_reason");
}
