import { onMessagePublished } from "firebase-functions/pubsub";
import { logger } from "firebase-functions";
import { defineBoolean } from "firebase-functions/params";
import { GoogleAuth } from "google-auth-library";
import { REGION } from "./config";
import { db, Timestamp } from "./db";

/**
 * Spending guard. Firebase has no hard spending limit, so a Cloud Billing budget publishes its
 * status to the Pub/Sub topic below a few times a day and this function reacts:
 *   >= SOFT_RATIO of budget → "paused": new first letters are refused (the most read-heavy call);
 *                             replies, inbox pickup and reports keep working.
 *   >= 100% of budget       → billing is detached from the project (hard cap). Everything that
 *                             needs Blaze stops until you re-link billing in the console.
 * Setup: docs/OPERATIONS.md §7.
 */
export const BUDGET_TOPIC = "billing-budget";
export const SOFT_RATIO = 0.9;
/** Set BILLING_HARD_CAP=false in functions/.env to keep only the soft pause. */
const HARD_CAP = defineBoolean("BILLING_HARD_CAP", { default: true });

export interface BudgetNotification {
  budgetDisplayName?: string;
  costAmount: number;
  budgetAmount: number;
  currencyCode?: string;
  costIntervalStart?: string;
}

export type BudgetAction = "none" | "soft" | "hard";

export function budgetAction(cost: number, budget: number, hardCap: boolean): BudgetAction {
  if (!(budget > 0) || !Number.isFinite(cost)) return "none";
  if (hardCap && cost >= budget) return "hard";
  if (cost >= budget * SOFT_RATIO) return "soft";
  return "none";
}

const runtimeRef = () => db.collection("config").doc("runtime");

async function disableBilling(projectId: string): Promise<boolean> {
  const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-billing"] });
  const client = await auth.getClient();
  const url = `https://cloudbilling.googleapis.com/v1/projects/${projectId}/billingInfo`;
  const info = await client.request<{ billingEnabled?: boolean }>({ url });
  if (!info.data.billingEnabled) return false;
  await client.request({ url, method: "PUT", data: { billingAccountName: "" } });
  return true;
}

export const onBudgetAlert = onMessagePublished<BudgetNotification>({ topic: BUDGET_TOPIC, region: REGION }, async (event) => {
  const n = event.data.message.json;
  const action = budgetAction(Number(n.costAmount), Number(n.budgetAmount), HARD_CAP.value());
  const snap = await runtimeRef().get();
  const pausedByBudget = snap.get("paused") === true && snap.get("pausedBy") === "budget";

  if (action === "none") {
    // New billing month (cost reset): lift a pause this guard set earlier.
    if (pausedByBudget) {
      await runtimeRef().set({ paused: false, pausedBy: null, at: Timestamp.now() }, { merge: true });
      logger.info("budget pause lifted", { cost: n.costAmount, budget: n.budgetAmount });
    }
    return;
  }

  if (!snap.get("paused")) {
    await runtimeRef().set(
      { paused: true, pausedBy: "budget", at: Timestamp.now(), cost: n.costAmount, budget: n.budgetAmount },
      { merge: true },
    );
    logger.warn("budget soft limit reached: new letters paused", { cost: n.costAmount, budget: n.budgetAmount });
  }

  if (action === "hard") {
    const projectId = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT ?? "";
    const disabled = await disableBilling(projectId);
    logger.error("budget exceeded: billing disabled", { projectId, disabled, cost: n.costAmount, budget: n.budgetAmount });
  }
});

let cache: { paused: boolean; until: number } | null = null;

/** True while the spending guard (or an operator) has paused new letters. Cached for a minute per instance. */
export async function isPaused(): Promise<boolean> {
  if (cache && cache.until > Date.now()) return cache.paused;
  const snap = await runtimeRef().get();
  cache = { paused: snap.get("paused") === true, until: Date.now() + 60_000 };
  return cache.paused;
}

/** Tests only. */
export function clearPauseCache(): void {
  cache = null;
}
