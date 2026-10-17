import { describe, expect, it } from "vitest";
import { signLetter, verifyLetter } from "../src/signing";
import { ageFromBirthYear } from "../src/validation";

describe("letter signatures", () => {
  const f = { deliveryId: "d1", threadId: "t1", fromUid: "u1", sentAt: 1760000000000, body: "안녕하세요 반가워요" };
  it("verifies an untouched letter", () => {
    expect(verifyLetter("k", f, signLetter("k", f))).toBe(true);
  });
  it("rejects edited body, wrong author or wrong key", () => {
    const sig = signLetter("k", f);
    expect(verifyLetter("k", { ...f, body: "edited" }, sig)).toBe(false);
    expect(verifyLetter("k", { ...f, fromUid: "u2" }, sig)).toBe(false);
    expect(verifyLetter("other", f, sig)).toBe(false);
    expect(verifyLetter("k", f, "garbage")).toBe(false);
  });
});

describe("age", () => {
  it("is conservative with birth year only", () => {
    const now = new Date(Date.UTC(2026, 9, 17));
    expect(ageFromBirthYear(2008, now)).toBe(17);
    expect(ageFromBirthYear(2007, now)).toBe(18);
  });
});
