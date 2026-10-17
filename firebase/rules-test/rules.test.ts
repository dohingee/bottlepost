import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, where } from "firebase/firestore";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-bottlepost",
    firestore: { rules: readFileSync("../firestore.rules", "utf8"), host: "127.0.0.1", port: 8080 },
  });
});
afterAll(() => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "deliveries/d1"), { to: "alice", threadId: "t1", body: "hi there friend" });
    await setDoc(doc(db, "deliveries/d2"), { to: "bob", threadId: "t2", body: "hello stranger" });
    await setDoc(doc(db, "users/alice"), { gender: "F", status: "active" });
    await setDoc(doc(db, "threads/t1"), { participants: ["bob", "alice"] });
  });
});

describe("deliveries", () => {
  it("recipient can read and delete own letters", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(db, "deliveries/d1")));
    await assertSucceeds(getDocs(query(collection(db, "deliveries"), where("to", "==", "alice"))));
    await assertSucceeds(deleteDoc(doc(db, "deliveries/d1")));
  });
  it("others cannot read, list or delete them", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(db, "deliveries/d2")));
    await assertFails(getDocs(collection(db, "deliveries")));
    await assertFails(deleteDoc(doc(db, "deliveries/d2")));
  });
  it("nobody can create or edit letters from the client", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(db, "deliveries/x"), { to: "bob", body: "spam" }));
    await assertFails(setDoc(doc(db, "deliveries/d1"), { to: "alice", body: "edited" }));
  });
  it("unauthenticated users get nothing", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "deliveries/d1")));
  });
});

describe("everything else is server-only", () => {
  it("users, threads and reports are not client-accessible", async () => {
    const db = env.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(db, "users/alice")));
    await assertFails(setDoc(doc(db, "users/alice"), { status: "active", strikes: 0 }));
    await assertFails(getDoc(doc(db, "threads/t1")));
    await assertFails(setDoc(doc(db, "reports/r1"), { reason: "spam" }));
  });
});
