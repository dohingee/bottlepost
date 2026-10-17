/**
 * Free, deterministic rule engine. Used twice:
 *  - before sending (blocks contact info / links, the main vector for spam and romance scams)
 *  - when a report arrives (spam / scam scoring, which general moderation models don't cover)
 */

export interface RuleResult {
  /** Links, e-mails, phone numbers or messenger handles. Blocked at send time. */
  hasContact: boolean;
  hasCrypto: boolean;
  signals: string[];
  spamScore: number;
  scamScore: number;
}

const URL_RE = /\b(?:https?:\/\/|www\.)\S+|\b[a-z0-9-]{2,}\.(?:com|net|org|io|me|ly|gg|xyz|top|kr|jp|link|site|app|shop|club|info)\b(?:\/\S*)?/i;
const EMAIL_RE = /[a-z0-9._%+-]+\s*(?:@|\(at\)|\[at\])\s*[a-z0-9.-]+\s*(?:\.|\(dot\)|\[dot\])\s*[a-z]{2,}/i;
const MESSENGER_RE = new RegExp(
  [
    "kakao", "카톡", "카카오", "오픈\\s*채팅", "open\\s*chat",
    "telegram", "텔레그램", "텔레", "\\btg\\b",
    "whats\\s*app", "왓츠앱", "\\bline\\s*(?:id|아이디)", "라인\\s*아이디",
    "instagram", "인스타", "\\binsta\\b", "\\big\\s*[:@]",
    "snap\\s*chat", "스냅챗", "wechat", "위챗", "discord", "디스코드", "signal\\s*app", "viber", "zalo",
  ].join("|"),
  "i",
);
const BTC_RE = /\b(?:bc1|[13])[a-km-zA-HJ-NP-Z1-9]{25,39}\b/;
const ETH_RE = /\b0x[a-fA-F0-9]{40}\b/;
const TRON_RE = /\bT[a-km-zA-HJ-NP-Z1-9]{33}\b/;

const MONEY_TERMS = [
  "송금", "입금", "계좌", "대출", "투자", "수익률", "리딩방", "코인", "선물거래", "환전", "상품권", "기프트카드",
  "invest", "investment", "profit", "crypto", "bitcoin", "usdt", "forex", "trading platform", "gift card",
  "western union", "wire transfer", "bank account", "loan", "cash app", "paypal",
  "投資", "送金", "暗号資産", "inversión", "investimento", "đầu tư", "investasi", "ลงทุน",
];
const SPAM_TERMS = [
  "무료", "이벤트", "할인", "가입하면", "클릭", "광고", "홍보", "구독", "팔로우",
  "free", "promo", "discount", "click", "subscribe", "follow me", "sign up", "limited offer", "onlyfans", "cam",
];

function countDigits(s: string): number {
  return (s.match(/\d/g) ?? []).length;
}

/** Long digit runs with optional separators: phone numbers, account numbers, IDs. */
function hasPhoneLike(text: string): boolean {
  const runs = text.match(/\+?\d[\d\s().-]{6,}\d/g) ?? [];
  return runs.some((r) => countDigits(r) >= 8);
}

function normalize(text: string): string {
  // Collapse "k a k a o" style spacing and full-width characters used to dodge filters.
  return text.normalize("NFKC").replace(/(\S)\s(?=\S\s|\S$)/g, "$1");
}

function termHits(text: string, terms: string[]): string[] {
  const lower = text.toLowerCase();
  return terms.filter((t) => lower.includes(t.toLowerCase()));
}

export function analyzeText(raw: string): RuleResult {
  const text = raw.normalize("NFKC");
  const squeezed = normalize(raw);
  const signals: string[] = [];

  const url = URL_RE.test(text) || URL_RE.test(squeezed);
  const email = EMAIL_RE.test(text);
  const phone = hasPhoneLike(text);
  const messenger = MESSENGER_RE.test(text) || MESSENGER_RE.test(squeezed);
  const crypto = BTC_RE.test(text) || ETH_RE.test(text) || TRON_RE.test(text);
  if (url) signals.push("url");
  if (email) signals.push("email");
  if (phone) signals.push("phone");
  if (messenger) signals.push("messenger");
  if (crypto) signals.push("crypto_address");

  const money = termHits(text, MONEY_TERMS);
  const spam = termHits(text, SPAM_TERMS);
  if (money.length) signals.push(`money:${money.slice(0, 3).join(",")}`);
  if (spam.length) signals.push(`spam:${spam.slice(0, 3).join(",")}`);

  const hasContact = url || email || phone || messenger;
  const clamp = (n: number) => Math.min(1, Math.round(n * 100) / 100);
  const spamScore = clamp((url ? 0.5 : 0) + (messenger ? 0.3 : 0) + spam.length * 0.15);
  const scamScore = clamp((crypto ? 0.7 : 0) + money.length * 0.25 + (hasContact && money.length ? 0.3 : 0));

  return { hasContact, hasCrypto: crypto, signals, spamScore, scamScore };
}

/** Throws-free check used by sendLetter/replyLetter. Returns an error code or null. */
export function preSendViolation(body: string): string | null {
  const r = analyzeText(body);
  if (r.hasCrypto) return "crypto_not_allowed";
  if (r.hasContact) return "contact_info_not_allowed";
  return null;
}
