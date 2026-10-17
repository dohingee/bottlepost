// Renders docs/legal/*.md into firebase/hosting/*.html so the store-listed URLs always match the
// drafts in the repo. Handles the small Markdown subset those files use.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const pages = [
  ["terms_ko.md", "terms.html", "ko"],
  ["terms_en.md", "terms-en.html", "en"],
  ["privacy_ko.md", "privacy.html", "ko"],
  ["privacy_en.md", "privacy-en.html", "en"],
  ["child_safety.md", "child-safety.html", "en"],
];

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const inline = (s) =>
  esc(s)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`(.+?)`/g, "<code>$1</code>")
    .replace(/\[(.+?)\]\((https?:\/\/[^)]+)\)/g, '<a href="$2">$1</a>')
    // Bare URLs; sentence punctuation right after a URL is not part of it.
    .replace(/(^|[\s(])(https?:\/\/[^\s)<]*[^\s)<.,;:!?])/g, '$1<a href="$2">$2</a>');

function render(md) {
  const out = [];
  let list = null;
  let table = null;
  const flush = () => {
    if (list) out.push(`</${list}>`), (list = null);
    if (table) out.push(table.join("") + "</tbody></table>"), (table = null);
  };
  for (const line of md.split("\n")) {
    const t = line.trim();
    if (/^\|.*\|$/.test(t)) {
      const cells = t.slice(1, -1).split("|").map((c) => c.trim());
      if (cells.every((c) => /^-+$/.test(c))) continue;
      if (!table) {
        flush();
        table = [`<table><thead><tr>${cells.map((c) => `<th>${inline(c)}</th>`).join("")}</tr></thead><tbody>`];
      } else table.push(`<tr>${cells.map((c) => `<td>${inline(c)}</td>`).join("")}</tr>`);
      continue;
    }
    if (table) flush();
    let m;
    if ((m = t.match(/^(#{1,3}) (.*)/))) { flush(); out.push(`<h${m[1].length}>${inline(m[2])}</h${m[1].length}>`); }
    else if ((m = t.match(/^- (.*)/))) { if (list !== "ul") { flush(); out.push("<ul>"); list = "ul"; } out.push(`<li>${inline(m[1])}</li>`); }
    else if ((m = t.match(/^\d+\. (.*)/))) { if (list !== "ol") { flush(); out.push("<ol>"); list = "ol"; } out.push(`<li>${inline(m[1])}</li>`); }
    else if ((m = t.match(/^> (.*)/))) { flush(); out.push(`<p class="note">${inline(m[1])}</p>`); }
    else if (t === "") flush();
    else { flush(); out.push(`<p>${inline(t)}</p>`); }
  }
  flush();
  return out.join("\n");
}

for (const [src, dest, lang] of pages) {
  const md = readFileSync(join(root, "docs", "legal", src), "utf8");
  const title = (md.match(/^# (.*)/m)?.[1] ?? "Bottlepost").replace(/\s*\(.*\)$/, "");
  const html = `<!doctype html>
<html lang="${lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title><link rel="stylesheet" href="/style.css"></head>
<body><main>${render(md)}</main><footer><a href="/">Bottlepost</a></footer></body></html>
`;
  writeFileSync(join(root, "firebase", "hosting", dest), html);
  console.log(`wrote hosting/${dest}`);
}
