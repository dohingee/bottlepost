/**
 * Shared bootstrap for operator CLIs. They run with your own Google credentials against production:
 *   gcloud auth application-default login
 *   export GOOGLE_CLOUD_PROJECT=<firebase project id>
 * They reuse the Cloud Functions modules so every action matches what the bot does.
 */
if (!process.env.GOOGLE_CLOUD_PROJECT && !process.env.GCLOUD_PROJECT && !process.env.FIRESTORE_EMULATOR_HOST) {
  console.error("Set GOOGLE_CLOUD_PROJECT (or FIRESTORE_EMULATOR_HOST for the emulator) first.");
  process.exit(2);
}

export const ACTOR = `cli:${process.env.USER ?? "operator"}`;

export function usage(text: string): never {
  console.error(text.trim());
  process.exit(1);
}

export function fmtTime(v: unknown): string {
  const t = v as { toDate?: () => Date } | null | undefined;
  return t?.toDate ? t.toDate().toISOString().replace("T", " ").slice(0, 16) : "-";
}

export function table(rows: Record<string, unknown>[]): void {
  if (rows.length === 0) console.log("(none)");
  else console.table(rows);
}
