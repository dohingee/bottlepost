import type { Message } from "firebase-admin/messaging";
import type { DeliveryDoc } from "./types";

/**
 * FCM message for a delivery. Android gets data only and builds its own localized notification.
 * iOS cannot run code for a plain data message reliably, so APNs gets an alert whose title/body
 * are localization keys from the app's string catalog: shown in the recipient's language, never
 * with letter text. "left" notices stay silent on both (background wake-up only on iOS).
 * Kept free of firebase-admin runtime imports so it can be unit-tested.
 */
export function buildPushMessage(token: string, kind: DeliveryDoc["kind"]): Message {
  const data = { type: "letter", kind };
  const android = { priority: "high" as const };
  if (kind === "left") {
    return {
      token,
      data,
      android,
      apns: {
        headers: { "apns-push-type": "background", "apns-priority": "5" },
        payload: { aps: { contentAvailable: true } },
      },
    };
  }
  return {
    token,
    data,
    android,
    apns: {
      headers: { "apns-push-type": "alert", "apns-priority": "10" },
      payload: {
        aps: {
          alert: {
            titleLocKey: kind === "reply" ? "notification_reply_title" : "notification_letter_title",
            locKey: "notification_body",
          },
          sound: "default",
          contentAvailable: true,
        },
      },
    },
  };
}
