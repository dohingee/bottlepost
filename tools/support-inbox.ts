import { ACTOR, fmtTime, table, usage } from "./_env";
import { col, Timestamp } from "../firebase/functions/src/db";
import { restoreUser } from "../firebase/functions/src/enforcement";

const USAGE = `
Support inbox: in-app appeals and requests (users write them from Settings).
  npx tsx support-inbox.ts list
  npx tsx support-inbox.ts show <appealId>
  npx tsx support-inbox.ts close <appealId> <granted|denied> [note]   # granted also restores the account
`;

async function list() {
  const snap = await col.appeals().where("status", "==", "open").orderBy("createdAt").limit(100).get();
  table(snap.docs.map((d) => ({
    id: d.id,
    uid: d.get("uid"),
    kind: d.get("kind"),
    lang: d.get("lang"),
    userStatus: d.get("userStatus"),
    created: fmtTime(d.get("createdAt")),
    preview: String(d.get("message")).slice(0, 60),
  })));
}

async function show(id: string) {
  const snap = await col.appeals().doc(id).get();
  if (!snap.exists) usage(`appeal ${id} not found`);
  const a = snap.data()!;
  console.log(`appeal ${id} from ${a.uid} (${a.lang}) · status=${a.status} · user=${a.userStatus} · ${fmtTime(a.createdAt)}\n`);
  console.log(a.message);
  console.log(`\nrun: npx tsx moderation-cli.ts user ${a.uid}   to see the reports behind this suspension`);
}

async function close(id: string, outcome: string, ...note: string[]) {
  if (outcome !== "granted" && outcome !== "denied") usage("outcome must be granted or denied");
  const ref = col.appeals().doc(id);
  const snap = await ref.get();
  if (!snap.exists) usage(`appeal ${id} not found`);
  if (outcome === "granted") await restoreUser(snap.get("uid"), ACTOR, `appeal ${id}: ${note.join(" ")}`);
  await ref.update({ status: "closed", outcome, note: note.join(" "), closedBy: ACTOR, closedAt: Timestamp.now() });
  console.log(`appeal ${id} closed (${outcome}). Reply to the user by e-mail if they contacted support.`);
}

const [cmd, ...args] = process.argv.slice(2);
const run: Record<string, () => Promise<void>> = {
  list,
  show: () => show(args[0]),
  close: () => close(args[0], args[1], ...args.slice(2)),
};
if (!cmd || !run[cmd] || (cmd !== "list" && !args[0])) usage(USAGE);
run[cmd]().then(() => process.exit(0), (e) => { console.error(e); process.exit(1); });
