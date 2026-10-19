import { createHash } from "node:crypto";
import { emailHash } from "./hash";

/**
 * Ban-evasion key of the account a user linked for restore: Google (Android) or Sign in with Apple
 * (iPhone). Only the hash is stored (users/{uid}.emailHash, bans/{hash}); the Apple path never sees
 * an e-mail address because the app requests no scopes. No firebase-functions import (tools/).
 */
export type LinkProvider = "google.com" | "apple.com";

/** The parts of a Firebase ID token this needs. */
export interface IdentityClaims {
  email?: string;
  email_verified?: boolean;
  firebase?: { sign_in_provider?: string; identities?: Record<string, unknown> };
}

export type LinkedIdentity = { provider: LinkProvider; hash: string } | { error: "google_not_linked" | "email_unverified" };

/** Apple's "sub" is stable per developer team, so it survives reinstalls and new phones. */
export function appleHash(sub: string): string {
  return createHash("sha256").update(`apple:${sub}`).digest("hex");
}

export function linkedIdentity(token: IdentityClaims, allowed: LinkProvider[]): LinkedIdentity {
  const firebase = token.firebase;
  const has = (p: LinkProvider) => allowed.includes(p) && (firebase?.sign_in_provider === p || !!firebase?.identities?.[p]);
  if (has("google.com")) {
    if (!token.email || token.email_verified !== true) return { error: "email_unverified" };
    return { provider: "google.com", hash: emailHash(token.email) };
  }
  if (has("apple.com")) {
    const ids = firebase?.identities?.["apple.com"];
    const sub = Array.isArray(ids) ? ids[0] : undefined;
    if (typeof sub !== "string" || sub === "") return { error: "google_not_linked" };
    return { provider: "apple.com", hash: appleHash(sub) };
  }
  return { error: "google_not_linked" };
}
