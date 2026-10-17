/**
 * End-to-end backend flow against the Firestore + Auth emulators, calling the real handlers:
 * onboarding → send → pickup/delete → reply → report (signature check) → moderation bot → deletion.
 *   npm run test:emulator
 */
import { beforeAll, describe, expect, it } from "vitest";

process.env.LETTER_HMAC_KEY = "emulator-test-key";
process.env.OPENAI_API_KEY = "disabled";
process.env.GCLOUD_PROJECT = "demo-bottlepost";

const onEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Fn = { run: (req: any) => any };
const year = new Date().getUTCFullYear();

describe.skipIf(!onEmulator)("letter lifecycle", async () => {
  const fns = (await import("../src/index")) as unknown as Record<string, Fn>;
  const { db } = await import("../src/db");

  const call = (name: string, uid: string, data: object = {}) =>
    fns[name].run({ data, auth: { uid, token: { firebase: { sign_in_provider: "anonymous" } } }, rawRequest: {}, acceptsStreaming: false });

  const expectCode = async (p: Promise<unknown>, code: string) => {
    await expect(p).rejects.toMatchObject({ message: code });
  };

  /** What the device does: read own deliveries, store them, delete them (then the delete trigger runs). */
  const pickup = async (uid: string) => {
    const snap = await db.collection("deliveries").where("to", "==", uid).get();
    for (const d of snap.docs) {
      await d.ref.delete();
      await fns.onDeliveryDeleted.run({ params: { id: d.id }, data: d });
    }
    return snap.docs.map((d) => ({ id: d.id, ...(d.data() as Record<string, unknown>) })) as Array<Record<string, any>>;
  };

  beforeAll(async () => {
    const cols = ["users", "threads", "deliveries", "reports", "bans", "appeals"];
    for (const c of cols) await db.recursiveDelete(db.collection(c));
  });

  const A = "alice-uid", B = "bob-uid", C = "carol-uid", KID = "kid-uid";
  let threadId = "";
  let first: Record<string, any>;
  let reply: Record<string, any>;

  it("enforces 18+ and terms at onboarding", async () => {
    await expectCode(call("createProfile", KID, { gender: "F", birthYear: year - 15, lang: "ko", acceptedTerms: true }), "underage");
    await expectCode(call("createProfile", A, { gender: "M", birthYear: 1995, lang: "ko" }), "terms_not_accepted");
    await call("createProfile", A, { gender: "M", birthYear: 1995, lang: "ko", country: "KR", nickname: "바다", acceptedTerms: true });
    await call("createProfile", B, { gender: "F", birthYear: 1997, lang: "en", country: "US", acceptedTerms: true });
    // Carol only receives from women, so Alice's letters can never be matched to her.
    await call("createProfile", C, { gender: "F", birthYear: 1990, lang: "ja", receiveFrom: "F", acceptedTerms: true });
  });

  it("filters contact info and matches a random user of the chosen gender", async () => {
    await expectCode(call("sendLetter", A, { body: "카톡 아이디 알려줄게요 친하게 지내요", wantGender: "F" }), "contact_info_not_allowed");
    await expectCode(call("sendLetter", A, { body: "hello there my friend", wantGender: "M" }), "no_recipient_available");
    const sent = await call("sendLetter", A, { body: "안녕하세요, 바다 건너 낯선 당신에게 편지를 띄워요.", wantGender: "F" });
    expect(sent.remainingToday).toBe(2);
    threadId = sent.threadId;
    await expectCode(call("sendLetter", A, { body: "안녕하세요, 바다 건너 낯선 당신에게 편지를 띄워요.", wantGender: "F" }), "duplicate_letter");
  });

  it("relays the letter without exposing the sender and leaves no content on the server", async () => {
    expect((await db.doc(`users/${B}`).get()).get("pendingInbox")).toBe(1);
    const inbox = await pickup(B);
    expect(inbox).toHaveLength(1);
    first = inbox[0];
    expect(first.senderGender).toBe("M");
    expect(first.senderNickname).toBe("바다");
    expect(JSON.stringify(first)).not.toContain(A);
    expect((await db.collection("deliveries").get()).size).toBe(0);
    expect((await db.doc(`users/${B}`).get()).get("pendingInbox")).toBe(0);
  });

  it("routes replies by thread id only", async () => {
    reply = await call("replyLetter", B, { threadId, body: "Hi! Your letter made my day. How is the sea?" });
    expect(await pickup(A)).toHaveLength(1);
    expect((await db.doc(`threads/${threadId}`).get()).get("replied")).toBe(true);
    await expectCode(call("replyLetter", C, { threadId, body: "can I join this conversation?" }), "not_a_participant");
  });

  it("verifies reported letters by signature and runs the moderation bot", async () => {
    await call("replyLetter", A, { threadId, body: "send me 500 usdt for crypto investment profit, trust me please" });
    const [scam] = await pickup(B);
    const res = await call("submitReport", B, {
      threadId,
      reason: "scam",
      messages: [
        { deliveryId: first.id, body: first.body + " (edited)", sentAt: first.sentAt, sig: first.sig, direction: "received" },
        { deliveryId: reply.deliveryId, body: "Hi! Your letter made my day. How is the sea?", sentAt: reply.sentAt, sig: reply.sig, direction: "sent" },
        { deliveryId: scam.id, body: scam.body, sentAt: scam.sentAt, sig: scam.sig, direction: "received" },
      ],
    });
    const snap = await db.doc(`reports/${res.reportId}`).get();
    expect(snap.get("messages").map((m: { verified: boolean }) => m.verified)).toEqual([false, true, true]);

    await fns.onReportCreated.run({ params: { reportId: res.reportId }, data: snap });
    const done = (await db.doc(`reports/${res.reportId}`).get()).data()!;
    // Scam rules fire without the AI classifier: 30-day suspension + strike.
    expect(done.verdict).toBe("suspend");
    const alice = (await db.doc(`users/${A}`).get()).data()!;
    expect(alice.status).toBe("suspended");
    expect(alice.strikes).toBe(1);
    await expectCode(call("sendLetter", A, { body: "a completely new letter to someone", wantGender: "F" }), "account_suspended");
  });

  it("closes the thread after a report and lets the user appeal", async () => {
    expect((await db.doc(`threads/${threadId}`).get()).get("closed")).toBe(true);
    await call("submitAppeal", A, { message: "I was hacked, please review" });
    await expectCode(call("submitAppeal", A, { message: "again please review" }), "appeal_pending");
  });

  it("deletes everything on account deletion", async () => {
    await call("deleteAccount", B);
    expect((await db.doc(`users/${B}`).get()).exists).toBe(false);
    expect((await db.collection("deliveries").where("to", "==", B).get()).size).toBe(0);
  });
});
