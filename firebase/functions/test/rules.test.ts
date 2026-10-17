import { describe, expect, it } from "vitest";
import { analyzeText, preSendViolation } from "../src/moderation/rules";

describe("preSendViolation", () => {
  it("allows a normal letter", () => {
    expect(preSendViolation("안녕하세요! 오늘 바다를 보러 다녀왔어요. 당신의 하루는 어땠나요?")).toBeNull();
    expect(preSendViolation("I am 25 and I love hiking, see you around in 2026!")).toBeNull();
  });

  it.each([
    ["https://example.com 놀러오세요"],
    ["check www.mysite.xyz"],
    ["카톡 아이디 알려드릴게요"],
    ["add me on telegram"],
    ["k a k a o 로 연락주세요"],
    ["my number is +82 10-1234-5678"],
    ["email me: hello (at) gmail (dot) com"],
    ["insta: @someone"],
  ])("blocks contact info: %s", (text) => {
    expect(preSendViolation(text)).toBe("contact_info_not_allowed");
  });

  it("blocks crypto addresses", () => {
    expect(preSendViolation("send to 0x52908400098527886E0F7030069857D2E4169EE7 please")).toBe("crypto_not_allowed");
  });
});

describe("analyzeText", () => {
  it("scores romance-scam patterns highly", () => {
    const r = analyzeText("I made big profit with crypto investment, send USDT to my wallet T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb");
    expect(r.scamScore).toBeGreaterThanOrEqual(0.9);
  });
  it("scores spam with links", () => {
    const r = analyzeText("free promo! click www.deal.shop and subscribe");
    expect(r.spamScore).toBeGreaterThanOrEqual(0.8);
  });
  it("keeps benign text low", () => {
    const r = analyzeText("오늘 날씨가 좋아서 산책했어요. 좋아하는 계절이 있나요?");
    expect(r.spamScore).toBe(0);
    expect(r.scamScore).toBe(0);
  });
});
