import { defineSecret } from "firebase-functions/params";

/** HMAC key that signs every letter so reports can prove a letter is genuine without the server keeping it. */
export const LETTER_HMAC_KEY = defineSecret("LETTER_HMAC_KEY");

/**
 * Optional AI classifier key. Set the secret to "disabled" to run without it: reports then use
 * rules + human review only.
 */
export const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");
