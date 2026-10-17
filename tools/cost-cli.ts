import { ACTOR, fmtTime, usage } from "./_env";
import { col, db, Timestamp } from "../firebase/functions/src/db";
import { LIMITS } from "../firebase/functions/src/config";

const USAGE = `
Cost guard: check the spending pause and estimate Firestore usage against the free quota.
  npx tsx cost-cli.ts status
  npx tsx cost-cli.ts pause [reason]     # stop new first letters now (replies keep working)
  npx tsx cost-cli.ts resume
`;

// Rough per-user daily usage measured from the code paths (docs/OPERATIONS.md §7).
const PER_DAU = { reads: 60, writes: 32 };
const FREE = { reads: 50_000, writes: 20_000 };

async function status() {
  const runtime = await db.collection("config").doc("runtime").get();
  console.log(`new letters: ${runtime.get("paused") ? `PAUSED by ${runtime.get("pausedBy")} at ${fmtTime(runtime.get("at"))}` : "running"}`);
  if (runtime.get("cost") !== undefined) console.log(`last budget alert: cost ${runtime.get("cost")} / budget ${runtime.get("budget")}`);

  const since = Timestamp.fromMillis(Date.now() - 86_400_000);
  const dau = (await col.users().where("lastActive", ">=", since).count().get()).data().count;
  const reads = dau * PER_DAU.reads;
  const writes = dau * PER_DAU.writes;
  const pct = (v: number, f: number) => `${Math.round((v / f) * 100)}%`;
  console.log(`active last 24h: ${dau} users (lastActive is written at most every ${LIMITS.activeTouchMinutes} min)`);
  console.log(`estimated Firestore per day: reads ~${reads} (${pct(reads, FREE.reads)} of free), writes ~${writes} (${pct(writes, FREE.writes)} of free)`);
  console.log(`free quota lasts until roughly ${Math.floor(Math.min(FREE.reads / PER_DAU.reads, FREE.writes / PER_DAU.writes))} daily active users.`);
  console.log("Exact numbers: Firebase console > Usage and billing.");
}

async function setPaused(paused: boolean, reason: string) {
  await db.collection("config").doc("runtime").set(
    { paused, pausedBy: paused ? `${ACTOR}: ${reason}` : null, at: Timestamp.now() },
    { merge: true },
  );
  console.log(paused ? "new letters paused (takes effect within a minute)" : "new letters resumed");
}

const [cmd, ...rest] = process.argv.slice(2);
const run: Record<string, () => Promise<void>> = {
  status,
  pause: () => setPaused(true, rest.join(" ") || "manual"),
  resume: () => setPaused(false, ""),
};
if (!cmd || !run[cmd]) usage(USAGE);
run[cmd]().then(() => process.exit(0), (e) => { console.error(e); process.exit(1); });
