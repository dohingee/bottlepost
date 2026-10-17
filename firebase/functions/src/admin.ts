import { onCall, HttpsError } from "firebase-functions/https";
import { callableOpts, requireAdmin } from "./common";
import { col } from "./db";
import { applyAction, resolveReport, restoreUser } from "./enforcement";
import type { Verdict } from "./types";
import { requireId } from "./validation";

/**
 * Admin callables for a future web console. Operators currently use tools/moderation-cli.ts,
 * which calls the same enforcement functions directly. Grant access with:
 *   npx tsx tools/account-cli.ts grant-admin <uid>
 */
const VERDICTS: Verdict[] = ["ban_permanent", "suspend", "dismiss"];

export const adminReviewQueue = onCall(callableOpts, async (req) => {
  requireAdmin(req);
  const snap = await col.reports().where("status", "==", "review").orderBy("createdAt").limit(50).get();
  return snap.docs.map((d) => ({ id: d.id, ...d.data(), createdAt: d.get("createdAt")?.toMillis() }));
});

export const adminResolveReport = onCall(callableOpts, async (req) => {
  const actor = requireAdmin(req);
  const reportId = requireId(req.data?.reportId, "report");
  const verdict = req.data?.verdict as Verdict;
  if (!VERDICTS.includes(verdict)) throw new HttpsError("invalid-argument", "invalid_verdict");
  const days = Math.max(0, Math.min(365, Number(req.data?.suspendDays ?? 7)));

  const snap = await col.reports().doc(reportId).get();
  if (!snap.exists) throw new HttpsError("not-found", "report_not_found");
  const reportedUid = snap.get("reportedUid") as string;

  await resolveReport(reportId, verdict, { reviewedBy: actor });
  if (verdict === "dismiss") {
    // A bot-suspended user whose report is dismissed gets restored.
    const user = await col.users().doc(reportedUid).get();
    if (user.get("status") === "suspended") await restoreUser(reportedUid, actor, `report ${reportId} dismissed`);
  } else {
    await applyAction({ uid: reportedUid, verdict, suspendDays: days, suspendNow: true, addStrike: true, actor, reportId });
  }
  return { ok: true };
});
