import { createHmac, timingSafeEqual } from "node:crypto";

/**
 * Letters are signed at send time and the signature travels with the letter to both devices.
 * When a user reports, the server re-computes the signature to prove the reported text is what
 * was actually sent, without ever having stored the letter.
 */
export interface SignedFields {
  deliveryId: string;
  threadId: string;
  fromUid: string;
  sentAt: number;
  body: string;
}

function payload(f: SignedFields): string {
  return JSON.stringify([f.deliveryId, f.threadId, f.fromUid, f.sentAt, f.body]);
}

export function signLetter(key: string, f: SignedFields): string {
  return createHmac("sha256", key).update(payload(f)).digest("base64url");
}

export function verifyLetter(key: string, f: SignedFields, sig: string): boolean {
  const expected = Buffer.from(signLetter(key, f));
  const given = Buffer.from(String(sig));
  return expected.length === given.length && timingSafeEqual(expected, given);
}
