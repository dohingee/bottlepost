import { describe, expect, it } from "vitest";
import { decide, type DecisionInput } from "../src/moderation/decision";
import type { ModerationScores } from "../src/moderation/openaiModeration";
import { analyzeText } from "../src/moderation/rules";

const zero: ModerationScores = {
  sexual: 0, sexualMinors: 0, harassment: 0, harassmentThreatening: 0, hate: 0,
  hateThreatening: 0, violence: 0, selfHarm: 0, illicit: 0, flagged: false,
};
const base: DecisionInput = {
  reason: "harassment",
  ai: zero,
  rules: analyzeText("hello there"),
  distinctReporters30d: 1,
  priorStrikes: 0,
  hasVerifiedEvidence: true,
};

describe("decide", () => {
  it("dismisses clean single reports", () => {
    expect(decide(base).verdict).toBe("dismiss");
  });
  it("never dismisses minor-safety reports and suspends immediately", () => {
    const d = decide({ ...base, reason: "minor" });
    expect(d.verdict).toBe("review");
    expect(d.suspendNow).toBe(true);
    expect(d.severity).toBe("critical");
  });
  it("bans on high sexual/minors score", () => {
    expect(decide({ ...base, ai: { ...zero, sexualMinors: 0.9 } }).verdict).toBe("ban_permanent");
  });
  it("suspends on severe harassment and escalates to ban on the third strike", () => {
    const ai = { ...zero, harassmentThreatening: 0.95 };
    expect(decide({ ...base, ai }).verdict).toBe("suspend");
    expect(decide({ ...base, ai, priorStrikes: 2 }).verdict).toBe("ban_permanent");
  });
  it("sends moderate scores to human review", () => {
    expect(decide({ ...base, ai: { ...zero, harassment: 0.6 } }).verdict).toBe("review");
  });
  it("uses rules for scams the AI does not cover", () => {
    const rules = analyzeText("crypto investment profit, send usdt to 0x52908400098527886E0F7030069857D2E4169EE7");
    const d = decide({ ...base, reason: "scam", rules });
    expect(d.verdict).toBe("suspend");
    expect(d.suspendDays).toBe(30);
  });
  it("falls back to review when the AI is unavailable", () => {
    expect(decide({ ...base, ai: null }).verdict).toBe("review");
  });
  it("ignores unverified evidence unless many people report", () => {
    expect(decide({ ...base, hasVerifiedEvidence: false }).verdict).toBe("dismiss");
    expect(decide({ ...base, hasVerifiedEvidence: false, distinctReporters30d: 4 }).verdict).toBe("review");
  });
  it("acts on crowds of independent reporters", () => {
    expect(decide({ ...base, distinctReporters30d: 6 }).verdict).toBe("suspend");
  });
});
