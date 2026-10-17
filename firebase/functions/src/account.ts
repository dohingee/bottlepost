import { onCall } from "firebase-functions/https";
import { onSchedule } from "firebase-functions/scheduler";
import { logger } from "firebase-functions";
import { Timestamp } from "firebase-admin/firestore";
import { REGION } from "./config";
import { callableOpts, requireUid } from "./common";
import { col, db } from "./db";
import { deleteUserData } from "./accountData";

/**
 * In-app "Delete account" and the web deletion page both call this. App Check is not enforced so
 * the web page works without reCAPTCHA setup; the caller must still be signed in as the user.
 */
export const deleteAccount = onCall({ ...callableOpts, enforceAppCheck: false }, async (req) => {
  const uid = requireUid(req);
  await deleteUserData(uid);
  return { ok: true };
});

/** Daily housekeeping: lift expired suspensions so matching includes those users again. */
export const liftExpiredSuspensions = onSchedule({ region: REGION, schedule: "every day 04:00", timeZone: "Asia/Seoul" }, async () => {
  const expired = await col
    .users()
    .where("status", "==", "suspended")
    .where("suspendedUntil", "<=", Timestamp.now())
    .limit(500)
    .get();
  const writer = db.bulkWriter();
  expired.docs.forEach((d) => writer.update(d.ref, { status: "active", suspendedUntil: null }));
  await writer.close();
  logger.info("lifted suspensions", { count: expired.size });
});
