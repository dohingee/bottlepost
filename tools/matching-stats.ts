/**
 * Matching health: is the gender/language pool balanced, are letters being delivered?
 *   npx tsx matching-stats.ts
 */
import "./_env";
import { col, Timestamp } from "../firebase/functions/src/db";
import { LIMITS } from "../firebase/functions/src/config";

async function main() {
  const activeSince = Timestamp.fromMillis(Date.now() - LIMITS.activeWindowDays * 86_400_000);
  const users = await col.users().where("status", "==", "active").select("gender", "lang", "lastActive", "pendingInbox", "receiveFrom", "linked").get();
  const pool: Record<string, { total: number; active: number; fullInbox: number }> = {};
  const langs: Record<string, number> = {};
  let linked = 0;
  for (const d of users.docs) {
    const g = d.get("gender") as string;
    const p = (pool[g] ??= { total: 0, active: 0, fullInbox: 0 });
    p.total++;
    const isActive = ((d.get("lastActive") as Timestamp | undefined)?.toMillis() ?? 0) >= activeSince.toMillis();
    if (isActive) {
      p.active++;
      langs[d.get("lang")] = (langs[d.get("lang")] ?? 0) + 1;
    }
    if ((d.get("pendingInbox") ?? 0) >= LIMITS.maxPendingInbox) p.fullInbox++;
    if (d.get("linked")) linked++;
  }
  console.log(`active accounts: ${users.size} (Google-linked ${linked})`);
  console.table(pool);
  console.log("active users by language:");
  console.table(langs);

  const deliveries = await col.deliveries().select("sentAt", "kind").get();
  const now = Date.now();
  const stale = deliveries.docs.filter((d) => now - (d.get("sentAt") as number) > 3 * 86_400_000).length;
  console.log(`letters waiting on relay: ${deliveries.size} (older than 3 days: ${stale})`);

  const weekAgo = Timestamp.fromMillis(now - 7 * 86_400_000);
  const threads = await col.threads().where("createdAt", ">=", weekAgo).select("replied", "closed").get();
  const replied = threads.docs.filter((d) => d.get("replied")).length;
  const rate = threads.size ? Math.round((replied / threads.size) * 100) : 0;
  console.log(`new threads last 7 days: ${threads.size}, replied: ${replied} (${rate}%)`);

  const g = Object.entries(pool).map(([k, v]) => `${k}=${v.active}`).join(" ");
  console.log(`\nhints: active pool ${g}. A pool much smaller than demand means "no_recipient_available" errors;`);
  console.log(`consider widening activeWindowDays or maxPendingInbox in firebase/functions/src/config.ts.`);
}

main().then(() => process.exit(0), (e) => { console.error(e); process.exit(1); });
