/**
 * Ads config (for later, once AdMob is approved): writes a Firebase Remote Config template with the
 * pre-send interstitial settings the app's AdMob gate should read, and checks them against policy.
 *   npx tsx ad-config.ts [--enable] [--interval 180] > remoteconfig.template.json
 *   firebase deploy --only remoteconfig   (with "remoteconfig": {"template": "remoteconfig.template.json"})
 */
const args = process.argv.slice(2);
const enabled = args.includes("--enable");
const idx = args.indexOf("--interval");
const interval = idx >= 0 ? Number(args[idx + 1]) : 180;

const problems: string[] = [];
// Interstitials before every send would be an unexpected, disruptive ad experience.
if (!Number.isFinite(interval) || interval < 60) problems.push("interval must be at least 60 seconds between interstitials");
if (problems.length) {
  console.error(problems.join("\n"));
  process.exit(1);
}

const param = (value: string, description: string) => ({ defaultValue: { value }, description, valueType: typeof value === "string" && /^(true|false)$/.test(value) ? "BOOLEAN" : "NUMBER" });

const template = {
  parameters: {
    ads_enabled: param(String(enabled), "Master switch for the pre-send interstitial (AdGate)."),
    ads_interstitial_min_interval_sec: param(String(interval), "Minimum seconds between two interstitials."),
    ads_skip_first_letters: param("1", "Number of first letters a new user sends without any ad."),
    ads_max_per_day: param("5", "Daily cap per user."),
  },
};
console.log(JSON.stringify(template, null, 2));
