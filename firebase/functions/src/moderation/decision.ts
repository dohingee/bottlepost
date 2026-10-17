import type { ReportReason } from "../config";
import type { Verdict } from "../types";
import type { ModerationScores } from "./openaiModeration";
import type { RuleResult } from "./rules";

export interface DecisionInput {
  reason: ReportReason;
  /** Scores for the reported user's verified letters only. */
  ai: ModerationScores | null;
  rules: RuleResult;
  /** Distinct users who reported this user in the last 30 days, including this report. */
  distinctReporters30d: number;
  priorStrikes: number;
  /** False when no reported letter passed signature verification. */
  hasVerifiedEvidence: boolean;
}

export interface Decision {
  verdict: Verdict;
  /** Suspend immediately even while waiting for human review. */
  suspendNow: boolean;
  suspendDays: number;
  addStrike: boolean;
  severity: "critical" | "high" | "medium" | "low";
  why: string[];
}

export const THRESHOLDS = {
  minorsReview: 0.2,
  minorsAuto: 0.6,
  severeAuto: 0.85,
  severeReview: 0.4,
  scamAuto: 0.9,
  scamReview: 0.5,
  spamAuto: 0.8,
  crowdReview: 3,
  crowdAuto: 6,
  strikesForPermanent: 3,
};

/**
 * Turns signals into an action. Conservative by design: automated actions are temporary
 * suspensions; permanent bans come from repeated strikes or a human reviewer, except CSAE.
 */
export function decide(i: DecisionInput): Decision {
  const why: string[] = [];
  const T = THRESHOLDS;
  const strike = (days: number, severity: Decision["severity"]): Decision => {
    const permanent = i.priorStrikes + 1 >= T.strikesForPermanent;
    if (permanent) why.push(`strikes ${i.priorStrikes + 1} >= ${T.strikesForPermanent}`);
    return {
      verdict: permanent ? "ban_permanent" : "suspend",
      suspendNow: true,
      suspendDays: days,
      addStrike: true,
      severity,
      why,
    };
  };

  if (!i.hasVerifiedEvidence) {
    why.push("no verified letters");
    if (i.distinctReporters30d >= T.crowdReview) {
      why.push(`${i.distinctReporters30d} distinct reporters`);
      return { verdict: "review", suspendNow: false, suspendDays: 0, addStrike: false, severity: "medium", why };
    }
    return { verdict: "dismiss", suspendNow: false, suspendDays: 0, addStrike: false, severity: "low", why };
  }

  const ai = i.ai;
  const minors = ai?.sexualMinors ?? 0;

  // Child safety: never auto-dismiss, always suspend and send to a human.
  if (i.reason === "minor" || minors >= T.minorsReview) {
    why.push(`minor-safety (reason=${i.reason}, sexual/minors=${minors.toFixed(2)})`);
    if (minors >= T.minorsAuto) {
      return { verdict: "ban_permanent", suspendNow: true, suspendDays: 0, addStrike: true, severity: "critical", why };
    }
    return { verdict: "review", suspendNow: true, suspendDays: 0, addStrike: false, severity: "critical", why };
  }

  if (ai) {
    const severe = Math.max(ai.harassmentThreatening, ai.hateThreatening, ai.violence, ai.sexual, ai.selfHarm);
    if (severe >= T.severeAuto) {
      why.push(`severe AI score ${severe.toFixed(2)}`);
      return strike(7, "high");
    }
    const moderate = Math.max(severe, ai.harassment, ai.hate, ai.illicit);
    if (moderate >= T.severeReview) {
      why.push(`moderate AI score ${moderate.toFixed(2)}`);
      return { verdict: "review", suspendNow: false, suspendDays: 0, addStrike: false, severity: "medium", why };
    }
  } else {
    why.push("AI classifier unavailable");
  }

  if (i.rules.scamScore >= T.scamAuto) {
    why.push(`scam rules ${i.rules.scamScore} [${i.rules.signals.join(" ")}]`);
    return strike(30, "high");
  }
  if (i.rules.spamScore >= T.spamAuto) {
    why.push(`spam rules ${i.rules.spamScore} [${i.rules.signals.join(" ")}]`);
    return strike(3, "medium");
  }
  if (i.rules.scamScore >= T.scamReview) {
    why.push(`possible scam ${i.rules.scamScore}`);
    return { verdict: "review", suspendNow: false, suspendDays: 0, addStrike: false, severity: "medium", why };
  }

  if (i.distinctReporters30d >= T.crowdAuto) {
    why.push(`${i.distinctReporters30d} distinct reporters (auto)`);
    return strike(3, "medium");
  }
  if (i.distinctReporters30d >= T.crowdReview || !ai) {
    why.push(`${i.distinctReporters30d} distinct reporters`);
    return { verdict: "review", suspendNow: false, suspendDays: 0, addStrike: false, severity: "low", why };
  }

  why.push("no violation detected");
  return { verdict: "dismiss", suspendNow: false, suspendDays: 0, addStrike: false, severity: "low", why };
}
