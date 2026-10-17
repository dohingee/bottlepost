import { describe, expect, it } from "vitest";
import { budgetAction } from "../src/billing";

describe("budgetAction", () => {
  it("does nothing under 90%", () => expect(budgetAction(8.9, 10, true)).toBe("none"));
  it("pauses new letters from 90%", () => expect(budgetAction(9, 10, true)).toBe("soft"));
  it("detaches billing at 100% when the hard cap is on", () => expect(budgetAction(10, 10, true)).toBe("hard"));
  it("only pauses when the hard cap is off", () => expect(budgetAction(25, 10, false)).toBe("soft"));
  it("ignores malformed notifications", () => {
    expect(budgetAction(NaN, 10, true)).toBe("none");
    expect(budgetAction(5, 0, true)).toBe("none");
  });
});
