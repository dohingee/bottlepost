import { ACTOR, fmtTime, table, usage } from "./_env";
import { col } from "../firebase/functions/src/db";
import { applyAction, resolveReport, restoreUser } from "../firebase/functions/src/enforcement";
import type { ReportDoc, Verdict } from "../firebase/functions/src/types";

const USAGE = `
Moderation tool: work the human review queue the report bot leaves behind.
  npx tsx moderation-cli.ts queue
  npx tsx moderation-cli.ts show <reportId>
  npx tsx moderation-cli.ts resolve <reportId> <ban|suspend|dismiss> [days] [note]
  npx tsx moderation-cli.ts user <uid>
  npx tsx moderation-cli.ts restore <uid> [note]
`;

const VERDICT: Record<string, Verdict> = { ban: "ban_permanent", suspend: "suspend", dismiss: "dismiss" };

async function queue() {
  const snap = await col.reports().where("status", "==", "review").orderBy("createdAt").limit(100).get();
  const order = { critical: 0, high: 1, medium: 2, low: 3 } as Record<string, number>;
  const rows = snap.docs
    .map((d) => {
      const r = d.data() as ReportDoc & { severity?: string };
      return {
        id: d.id,
        severity: r.severity ?? "?",
        reason: r.reason,
        reported: r.reportedUid,
        verified: r.messages.filter((m) => m.verified && m.direction === "received").length,
        created: fmtTime(r.createdAt),
      };
    })
    .sort((a, b) => (order[a.severity] ?? 9) - (order[b.severity] ?? 9));
  table(rows);
}

async function show(id: string) {
  const snap = await col.reports().doc(id).get();
  if (!snap.exists) usage(`report ${id} not found`);
  const r = snap.data() as ReportDoc & { analysis?: { why?: string[] } };
  console.log(`report ${id} · reason=${r.reason} · status=${r.status} · verdict=${r.verdict ?? "-"}`);
  console.log(`reporter=${r.reporterUid} reported=${r.reportedUid} thread=${r.threadId} at ${fmtTime(r.createdAt)}`);
  if (r.note) console.log(`note: ${r.note}`);
  console.log(`bot: ${(r.analysis?.why ?? []).join("; ")}`);
  console.log("--- letters (oldest first) ---");
  for (const m of r.messages) {
    const who = m.direction === "received" ? "REPORTED" : "reporter";
    console.log(`[${who}${m.verified ? "" : " · UNVERIFIED"}] ${new Date(m.sentAt).toISOString()}\n${m.body}\n`);
  }
}

async function resolve(id: string, verdictArg: string, days = "7", ...note: string[]) {
  const verdict = VERDICT[verdictArg];
  if (!verdict) usage("verdict must be ban, suspend or dismiss");
  const snap = await col.reports().doc(id).get();
  if (!snap.exists) usage(`report ${id} not found`);
  const reportedUid = snap.get("reportedUid") as string;
  await resolveReport(id, verdict, { reviewedBy: ACTOR, reviewNote: note.join(" ") });
  if (verdict === "dismiss") {
    const user = await col.users().doc(reportedUid).get();
    if (user.get("status") === "suspended") await restoreUser(reportedUid, ACTOR, `report ${id} dismissed`);
  } else {
    await applyAction({
      uid: reportedUid,
      verdict,
      suspendDays: Number(days),
      suspendNow: true,
      addStrike: true,
      actor: ACTOR,
      reportId: id,
      note: note.join(" "),
    });
  }
  console.log(`report ${id}: ${verdict} applied to ${reportedUid}`);
}

async function user(uid: string) {
  const snap = await col.users().doc(uid).get();
  if (!snap.exists) usage(`user ${uid} not found`);
  const u = snap.data()!;
  console.log({ uid, status: u.status, strikes: u.strikes, suspendedUntil: fmtTime(u.suspendedUntil), linked: u.linked, created: fmtTime(u.createdAt) });
  const log = await snap.ref.collection("moderationLog").orderBy("at", "desc").limit(20).get();
  table(log.docs.map((d) => ({ at: fmtTime(d.get("at")), verdict: d.get("verdict"), actor: d.get("actor"), report: d.get("reportId"), note: d.get("note") })));
  const against = await col.reports().where("reportedUid", "==", uid).orderBy("createdAt", "desc").limit(20).get();
  table(against.docs.map((d) => ({ id: d.id, reason: d.get("reason"), verdict: d.get("verdict"), at: fmtTime(d.get("createdAt")) })));
}

const [cmd, ...args] = process.argv.slice(2);
const run: Record<string, (...a: string[]) => Promise<void>> = {
  queue,
  show,
  resolve: (id, v, d, ...n) => resolve(id, v, d, ...n),
  user,
  restore: async (uid, ...note) => {
    await restoreUser(uid, ACTOR, note.join(" "));
    console.log(`restored ${uid}`);
  },
};
if (!cmd || !run[cmd] || (cmd !== "queue" && !args[0])) usage(USAGE);
run[cmd](...args).then(() => process.exit(0), (e) => { console.error(e); process.exit(1); });
