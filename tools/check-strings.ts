/**
 * Localization check: every locale has every key, the same placeholders, and no leftover English.
 *   npx tsx check-strings.ts            (exit code 1 on problems; safe for CI)
 */
import { readdirSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const RES = join(__dirname, "..", "android", "app", "src", "main", "res");
const IOS_RES = join(__dirname, "..", "ios", "strings");

function parse(file: string): Map<string, { value: string; translatable: boolean }> {
  const out = new Map<string, { value: string; translatable: boolean }>();
  const xml = readFileSync(file, "utf8");
  for (const m of xml.matchAll(/<string name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
    out.set(m[1], { value: m[3], translatable: !/translatable="false"/.test(m[2]) });
  }
  return out;
}

const placeholders = (s: string) => (s.match(/%\d+\$[sd]/g) ?? []).sort().join(",");

let problems = 0;
const report = (msg: string) => {
  problems++;
  console.log(msg);
};

/** Checks one resource tree: the Android app, or the iOS-only additions (ios/strings, same format). */
function check(root: string, label: string): void {
  const base = parse(join(root, "values", "strings.xml"));
  const keys = [...base].filter(([, v]) => v.translatable).map(([k]) => k);
  const locales = readdirSync(root).filter((d) => /^values-[a-z]{2}(-r[A-Z]{2})?$/.test(d) && existsSync(join(root, d, "strings.xml")));
  for (const dir of locales) {
    const strings = parse(join(root, dir, "strings.xml"));
    for (const k of keys) {
      const s = strings.get(k);
      if (!s) {
        report(`${label}/${dir}: missing "${k}"`);
        continue;
      }
      if (placeholders(s.value) !== placeholders(base.get(k)!.value)) report(`${label}/${dir}: placeholder mismatch in "${k}"`);
      if (s.value === base.get(k)!.value && s.value.length > 12) report(`${label}/${dir}: "${k}" is identical to English (untranslated?)`);
      if (/(^|[^\\])'/.test(s.value)) report(`${label}/${dir}: unescaped apostrophe in "${k}"`);
    }
    for (const k of strings.keys()) if (!base.has(k)) report(`${label}/${dir}: extra key "${k}" not in values/`);
  }
  console.log(`${label}: ${locales.length} locales × ${keys.length} keys checked`);
}

check(RES, "android");
check(IOS_RES, "ios");
console.log(problems === 0 ? "OK" : `${problems} problem(s)`);
process.exit(problems === 0 ? 0 : 1);
