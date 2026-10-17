import { moderateTexts, type ModerationScores } from "./openaiModeration";

/**
 * Seam for the AI step of the report pipeline. The default is the free OpenAI Moderation API.
 * To trade a small cost for spam/scam awareness, add another implementation (e.g. an LLM
 * classifier that returns the same ModerationScores shape) and select it in `getClassifier`.
 */
export interface Classifier {
  readonly name: string;
  classify(texts: string[]): Promise<ModerationScores | null>;
}

export function getClassifier(openaiKey: string): Classifier {
  return {
    name: "openai-omni-moderation",
    classify: (texts) => moderateTexts(openaiKey, texts),
  };
}
