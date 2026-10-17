import { describe, expect, it } from "vitest";
import { isEligible, pickRecipient, type Candidate, type MatchRequest } from "../src/matching";

const now = Date.UTC(2026, 9, 17);
const req: MatchRequest = {
  senderUid: "me", senderGender: "M", senderLang: "ko", wantGender: "F", sameLanguageOnly: false,
  excluded: new Set(), existingPartners: new Set(), nowMs: now,
};
const cand = (over: Partial<Candidate>): Candidate => ({
  uid: "u1", gender: "F", receiveFrom: "ANY", status: "active", pendingInbox: 0, lastActiveMs: now, lang: "en", ...over,
});

describe("isEligible", () => {
  it("accepts a matching active user", () => expect(isEligible(cand({}), req)).toBe(true));
  it("rejects self, wrong gender, inactive, full inbox", () => {
    expect(isEligible(cand({ uid: "me" }), req)).toBe(false);
    expect(isEligible(cand({ gender: "M" }), req)).toBe(false);
    expect(isEligible(cand({ lastActiveMs: now - 8 * 86_400_000 }), req)).toBe(false);
    expect(isEligible(cand({ pendingInbox: 20 }), req)).toBe(false);
    expect(isEligible(cand({ status: "suspended" }), req)).toBe(false);
  });
  it("respects the recipient's receiveFrom preference", () => {
    expect(isEligible(cand({ receiveFrom: "F" }), req)).toBe(false);
    expect(isEligible(cand({ receiveFrom: "M" }), req)).toBe(true);
  });
  it("respects blocks, existing threads and language", () => {
    expect(isEligible(cand({}), { ...req, excluded: new Set(["u1"]) })).toBe(false);
    expect(isEligible(cand({}), { ...req, existingPartners: new Set(["u1"]) })).toBe(false);
    expect(isEligible(cand({ lang: "en" }), { ...req, sameLanguageOnly: true })).toBe(false);
  });
  it("ANY matches both genders", () => {
    expect(isEligible(cand({ gender: "M" }), { ...req, wantGender: "ANY" })).toBe(true);
  });
});

describe("pickRecipient", () => {
  it("returns null when nobody is eligible", () => {
    expect(pickRecipient([cand({ gender: "M" })], req)).toBeNull();
  });
  it("prefers the lightest inbox among the first candidates", () => {
    const list = [cand({ uid: "a", pendingInbox: 5 }), cand({ uid: "b", pendingInbox: 1 }), cand({ uid: "c", pendingInbox: 3 })];
    expect(pickRecipient(list, req)?.uid).toBe("b");
  });
});
