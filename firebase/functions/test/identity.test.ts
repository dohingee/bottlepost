import { describe, expect, it } from "vitest";
import { appleHash, linkedIdentity } from "../src/identity";
import { emailHash } from "../src/hash";

describe("linked identity", () => {
  const google = { email: "A@Example.com", email_verified: true, firebase: { identities: { "google.com": ["123"] } } };
  const apple = { firebase: { sign_in_provider: "apple.com", identities: { "apple.com": ["001234.abc"] } } };

  it("hashes the Google e-mail exactly as before", () => {
    expect(linkedIdentity(google, ["google.com"])).toEqual({ provider: "google.com", hash: emailHash("a@example.com") });
  });

  it("rejects an unverified Google e-mail", () => {
    expect(linkedIdentity({ ...google, email_verified: false }, ["google.com", "apple.com"])).toEqual({ error: "email_unverified" });
  });

  it("hashes the Apple sub, never an e-mail", () => {
    const id = linkedIdentity(apple, ["google.com", "apple.com"]);
    expect(id).toEqual({ provider: "apple.com", hash: appleHash("001234.abc") });
    expect(appleHash("001234.abc")).not.toEqual(emailHash("001234.abc"));
  });

  it("keeps confirmGoogleLink Google-only", () => {
    expect(linkedIdentity(apple, ["google.com"])).toEqual({ error: "google_not_linked" });
  });

  it("refuses anonymous users", () => {
    expect(linkedIdentity({ firebase: { sign_in_provider: "anonymous", identities: {} } }, ["google.com", "apple.com"])).toEqual({
      error: "google_not_linked",
    });
  });
});
