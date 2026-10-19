import { FieldValue } from "firebase-admin/firestore";
import { col, messaging } from "./db";
import { buildPushMessage } from "./pushMessage";
import type { DeliveryDoc } from "./types";

/**
 * Content-free push ("You've got a letter"); see pushMessage.ts for the Android/iOS payloads.
 * Pass knownToken when the recipient doc was already read, to save a billed read.
 * No firebase-functions import: accountData.ts (also used by tools/) sends "left" notices with it.
 */
export async function notify(toUid: string, kind: DeliveryDoc["kind"], knownToken?: string | null): Promise<void> {
  const token =
    knownToken !== undefined ? knownToken : ((await col.users().doc(toUid).get()).get("fcmToken") as string | undefined);
  if (!token) return;
  try {
    await messaging.send(buildPushMessage(token, kind));
  } catch (err) {
    console.info("push failed", { code: (err as { code?: string }).code });
    if ((err as { code?: string }).code === "messaging/registration-token-not-registered") {
      await col.users().doc(toUid).update({ fcmToken: FieldValue.delete() });
    }
  }
}
