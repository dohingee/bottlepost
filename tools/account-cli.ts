import { ACTOR, fmtTime, table, usage } from "./_env";
import { auth, col } from "../firebase/functions/src/db";
import { deleteUserData } from "../firebase/functions/src/accountData";
import { emailHash } from "../firebase/functions/src/hash";

const USAGE = `
Account tool: support requests about restoring, deleting or finding accounts.
  npx tsx account-cli.ts show <uid>
  npx tsx account-cli.ts find-email <email>      # Google-linked accounts only (matched by hash)
  npx tsx account-cli.ts delete <uid> --yes      # same as in-app deletion
  npx tsx account-cli.ts grant-admin <uid>       # moderator access for adminReviewQueue/adminResolveReport
  npx tsx account-cli.ts revoke-admin <uid>
`;

async function show(uid: string) {
  const snap = await col.users().doc(uid).get();
  if (!snap.exists) usage(`no profile for ${uid}`);
  const u = snap.data()!;
  const authUser = await auth.getUser(uid).catch(() => null);
  const backup = await col.userPrivate(uid).doc("backup").get();
  console.log({
    uid,
    status: u.status,
    gender: u.gender,
    birthYear: u.birthYear,
    lang: u.lang,
    linked: u.linked,
    providers: authUser?.providerData.map((p) => p.providerId) ?? [],
    hasBackupKey: backup.exists,
    pendingInbox: u.pendingInbox,
    sentToday: u.sentDay ? `${u.sentToday} on ${u.sentDay}` : 0,
    lastActive: fmtTime(u.lastActive),
    created: fmtTime(u.createdAt),
  });
  const threads = await col.threads().where("participants", "array-contains", uid).select("createdAt", "replied", "closed").limit(20).get();
  table(threads.docs.map((d) => ({ thread: d.id, created: fmtTime(d.get("createdAt")), replied: d.get("replied"), closed: d.get("closed") })));
}

async function findEmail(email: string) {
  const hash = emailHash(email);
  const users = await col.users().where("emailHash", "==", hash).select("status", "createdAt").get();
  const banned = (await col.bans().doc(hash).get()).exists;
  console.log(`hash ${hash.slice(0, 12)}… banned=${banned}`);
  table(users.docs.map((d) => ({ uid: d.id, status: d.get("status"), created: fmtTime(d.get("createdAt")) })));
}

async function del(uid: string, confirm?: string) {
  if (confirm !== "--yes") usage("add --yes to confirm permanent deletion");
  await deleteUserData(uid);
  console.log(`deleted ${uid} (by ${ACTOR})`);
}

async function setAdmin(uid: string, admin: boolean) {
  const user = await auth.getUser(uid);
  await auth.setCustomUserClaims(uid, { ...(user.customClaims ?? {}), admin });
  console.log(`${uid} admin=${admin}. The user must sign in again (or refresh the ID token).`);
}

const [cmd, a, b] = process.argv.slice(2);
const run: Record<string, () => Promise<void>> = {
  show: () => show(a),
  "find-email": () => findEmail(a),
  delete: () => del(a, b),
  "grant-admin": () => setAdmin(a, true),
  "revoke-admin": () => setAdmin(a, false),
};
if (!cmd || !run[cmd] || !a) usage(USAGE);
run[cmd]().then(() => process.exit(0), (e) => { console.error(e); process.exit(1); });
