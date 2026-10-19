import { describe, expect, it } from "vitest";
import { buildPushMessage } from "../src/pushMessage";

describe("push message", () => {
  it("keeps the Android data payload unchanged", () => {
    const m = buildPushMessage("t", "first") as { data: unknown; android: unknown };
    expect(m.data).toEqual({ type: "letter", kind: "first" });
    expect(m.android).toEqual({ priority: "high" });
  });

  it("shows a localized, content-free alert on iOS for letters", () => {
    const first = buildPushMessage("t", "first");
    const reply = buildPushMessage("t", "reply");
    expect(first.apns?.headers).toEqual({ "apns-push-type": "alert", "apns-priority": "10" });
    expect(first.apns?.payload?.aps.alert).toEqual({ titleLocKey: "notification_letter_title", locKey: "notification_body" });
    expect(reply.apns?.payload?.aps.alert).toEqual({ titleLocKey: "notification_reply_title", locKey: "notification_body" });
    expect(JSON.stringify(first)).not.toContain("body\":");
  });

  it("keeps 'left' notices silent", () => {
    const left = buildPushMessage("t", "left");
    expect(left.apns?.headers).toEqual({ "apns-push-type": "background", "apns-priority": "5" });
    expect(left.apns?.payload?.aps.alert).toBeUndefined();
    expect(left.apns?.payload?.aps.contentAvailable).toBe(true);
  });
});
