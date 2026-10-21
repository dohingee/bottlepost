/**
 * Generates the iOS string catalogs from the Android string resources, so translations live in one
 * place (android/app/src/main/res/values-*\/strings.xml) and both apps say the same thing. Text only
 * the iPhone app needs (Apple account, iCloud) lives in ios/strings/values-*\/strings.xml, same format.
 *   npx tsx ios-strings.ts            write ios/Bottlepost/Resources/{Localizable,InfoPlist}.xcstrings
 *   npx tsx ios-strings.ts --check    exit code 1 when the committed catalogs are out of date
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const ROOT = join(__dirname, "..");
const RES = join(ROOT, "android", "app", "src", "main", "res");
const IOS_RES = join(ROOT, "ios", "strings");
const OUT = join(ROOT, "ios", "Bottlepost", "Resources");
const projectYml = readFileSync(join(ROOT, "ios", "project.yml"), "utf8")
  .split("\n")
  .filter((line) => !line.trimStart().startsWith("#"))
  .join("\n");

/** Android resource folder -> iOS locale (Android "pt" is Brazilian Portuguese, "in" is Indonesian). */
const LOCALES: Record<string, string> = {
  values: "en",
  "values-ko": "ko",
  "values-ja": "ja",
  "values-es": "es",
  "values-pt": "pt-BR",
  "values-in": "id",
  "values-vi": "vi",
  "values-th": "th",
};

function parse(file: string): Map<string, string> {
  const out = new Map<string, string>();
  for (const m of readFileSync(file, "utf8").matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
    out.set(m[1], unescape(m[2]));
  }
  return out;
}

/** Android string escapes and format specifiers -> plain text with Foundation format specifiers. */
function unescape(s: string): string {
  return s
    .replace(/\\n/g, "\n")
    .replace(/\\(['"@?])/g, "$1")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, "&")
    .replace(/%(\d+)\$s/g, "%$1$@")
    .replace(/%(\d+)\$d/g, "%$1$ld");
}

const tables = Object.entries(LOCALES).map(([dir, locale]) => {
  const strings = parse(join(RES, dir, "strings.xml"));
  const extra = join(IOS_RES, dir, "strings.xml");
  if (existsSync(extra)) for (const [k, v] of parse(extra)) strings.set(k, v);
  return { locale, strings };
});
const keys = [...tables[0].strings.keys()].sort();

function catalog(entries: [string, (locale: string) => string | undefined][]) {
  const strings: Record<string, unknown> = {};
  for (const [key, lookup] of entries) {
    const localizations: Record<string, unknown> = {};
    for (const { locale } of tables) {
      const value = lookup(locale);
      if (value !== undefined) localizations[locale] = { stringUnit: { state: "translated", value } };
    }
    strings[key] = { extractionState: "manual", localizations };
  }
  return JSON.stringify({ sourceLanguage: "en", strings, version: "1.0" }, null, 2) + "\n";
}

const lookup = (key: string) => (locale: string) => {
  const t = tables.find((x) => x.locale === locale)!;
  return t.strings.get(key) ?? (locale === "en" ? undefined : tables[0].strings.get(key));
};

const files: Record<string, string> = {
  "Localizable.xcstrings": catalog(keys.map((k) => [k, lookup(k)])),
  "InfoPlist.xcstrings": catalog(
    (
      [
        ["CFBundleDisplayName", "app_name"],
        // App Tracking Transparency prompt (ads, M7).
        ["NSUserTrackingUsageDescription", "ios_att_purpose"],
      ] as const
    )
      // Only keys the app's Info.plist actually has (the tracking prompt only while ads are on).
      .filter(([plistKey, key]) => tables[0].strings.has(key) && (plistKey === "CFBundleDisplayName" || projectYml.includes(`${plistKey}:`)))
      .map(([plistKey, key]) => [plistKey, lookup(key)] as [string, (locale: string) => string | undefined]),
  ),
};

let stale = 0;
for (const [name, content] of Object.entries(files)) {
  const path = join(OUT, name);
  if (process.argv.includes("--check")) {
    if (!existsSync(path) || readFileSync(path, "utf8") !== content) {
      console.log(`${name} is out of date: run npx tsx tools/ios-strings.ts`);
      stale = 1;
    }
  } else {
    writeFileSync(path, content);
    console.log(`wrote ${path} (${tables.length} locales)`);
  }
}
process.exit(stale);
