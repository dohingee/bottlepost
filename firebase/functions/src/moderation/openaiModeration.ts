import { logger } from "firebase-functions";

/**
 * OpenAI Moderation endpoint (`omni-moderation-latest`). Free of charge for API accounts and
 * multilingual. Only reported letters are ever sent here (disclosed in the privacy policy).
 */
export interface ModerationScores {
  sexual: number;
  sexualMinors: number;
  harassment: number;
  harassmentThreatening: number;
  hate: number;
  hateThreatening: number;
  violence: number;
  selfHarm: number;
  illicit: number;
  flagged: boolean;
}

type RawScores = Record<string, number>;

function pick(s: RawScores, ...keys: string[]): number {
  return Math.max(0, ...keys.map((k) => s[k] ?? 0));
}

export function toScores(raw: { flagged: boolean; category_scores: RawScores }): ModerationScores {
  const s = raw.category_scores;
  return {
    sexual: pick(s, "sexual"),
    sexualMinors: pick(s, "sexual/minors"),
    harassment: pick(s, "harassment"),
    harassmentThreatening: pick(s, "harassment/threatening"),
    hate: pick(s, "hate"),
    hateThreatening: pick(s, "hate/threatening"),
    violence: pick(s, "violence", "violence/graphic"),
    selfHarm: pick(s, "self-harm", "self-harm/intent", "self-harm/instructions"),
    illicit: pick(s, "illicit", "illicit/violent"),
    flagged: raw.flagged,
  };
}

/** Returns the max score per category across all texts, or null if the API is unavailable. */
export async function moderateTexts(apiKey: string, texts: string[]): Promise<ModerationScores | null> {
  if (!apiKey || texts.length === 0) return null;
  try {
    const res = await fetch("https://api.openai.com/v1/moderations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({ model: "omni-moderation-latest", input: texts }),
      signal: AbortSignal.timeout(15_000),
    });
    if (!res.ok) {
      logger.warn("moderation api error", { status: res.status });
      return null;
    }
    const json = (await res.json()) as { results: { flagged: boolean; category_scores: RawScores }[] };
    return json.results.map(toScores).reduce(maxScores);
  } catch (err) {
    logger.warn("moderation api unreachable", { err: String(err) });
    return null;
  }
}

function maxScores(a: ModerationScores, b: ModerationScores): ModerationScores {
  const out = { ...a };
  for (const k of Object.keys(a) as (keyof ModerationScores)[]) {
    if (k === "flagged") out.flagged = a.flagged || b.flagged;
    else out[k] = Math.max(a[k], b[k]);
  }
  return out;
}
