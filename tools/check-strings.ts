/**
 * Localization check: every locale has every key, the same placeholders, and no leftover English.
 *   npx tsx check-strings.ts            (exit code 1 on problems; safe for CI)
 */
import { readdirSync, readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const RES = join(__dirname, "..", "android", "app", "src", "main", "res");

function parse(file: string): Map<string, { value: string; translatable: boolean }> {
  const out = new Map<string, { value: string; translatable: boolean }>();
  const xml = readFileSync(file, "utf8");
  for (const m of xml.matchAll(/<string name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
    out.set(m[1], { value: m[3], translatable: !/translatable="false"/.test(m[2]) });
  }
  return out;
}

const placeholders = (s: string) => (s.match(/%\d+\$[sd]/g) ?? []).sort().join(",");

const base = parse(join(RES, "values", "strings.xml"));
const keys = [...base].filter(([, v]) => v.translatable).map(([k]) => k);
const locales = readdirSync(RES).filter((d) => /^values-[a-z]{2}(-r[A-Z]{2})?$/.test(d) && existsSync(join(RES, d, "strings.xml")));

let problems = 0;
const report = (msg: string) => {
  problems++;
  console.log(msg);
};

for (const dir of locales) {
  const strings = parse(join(RES, dir, "strings.xml"));
  for (const k of keys) {
    const s = strings.get(k);
    if (!s) {
      report(`${dir}: missing "${k}"`);
      continue;
    }
    if (placeholders(s.value) !== placeholders(base.get(k)!.value)) report(`${dir}: placeholder mismatch in "${k}"`);
    if (s.value === base.get(k)!.value && s.value.length > 12) report(`${dir}: "${k}" is identical to English (untranslated?)`);
    if (/(^|[^\\])'/.test(s.value)) report(`${dir}: unescaped apostrophe in "${k}"`);
  }
  for (const k of strings.keys()) if (!base.has(k)) report(`${dir}: extra key "${k}" not in values/`);
}

console.log(`${locales.length} locales × ${keys.length} keys checked: ${problems === 0 ? "OK" : `${problems} problem(s)`}`);
process.exit(problems === 0 ? 0 : 1);
