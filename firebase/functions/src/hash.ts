import { createHash } from "node:crypto";

/** Ban-evasion key for Google-linked accounts. Only the hash is stored, never the address. */
export function emailHash(email: string): string {
  return createHash("sha256").update(email.trim().toLowerCase()).digest("hex");
}
