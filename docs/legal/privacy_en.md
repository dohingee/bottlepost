# Bottlepost Privacy Policy (Draft)

> Draft. The Korean version (`privacy_ko.md`) has the full retention table and prevails in case of conflict.

**What we collect**
- Required: gender, birth year (to confirm you are 18+), app language/country, preferred recipient gender.
- Automatic: account ID (Firebase UID), push token, last-active time, send counters, conversation links (conversation ID and participant IDs — **never letter content**), crash logs, device-integrity verdicts.
- Optional: nickname, up to 3 interest tags, Google account ID/email when you link Google.
- When you report: the reported letter, up to 5 preceding letters in that conversation, and the reason. Kept 90 days after resolution.
- When banned: account ID, a one-way hash of the linked Google email, and the reason, to prevent ban evasion (1 year after a temporary ban ends, 3 years for permanent bans).

**What we never collect:** real name, phone number, precise location, contacts, photos, hardware device identifiers.

**Letter content.** A letter waits on our relay (Google Cloud Firestore) only until it reaches the recipient's device and is deleted immediately afterwards; undelivered letters are deleted after 7 days. Conversations live on your device. Optional backups are encrypted and stored in your own Google Drive app-data folder, which we cannot access. Translation runs on-device (Google ML Kit).

**Processors.** Google (Firebase/Google Cloud: hosting, auth, push, crash reporting, integrity); OpenAI (Moderation API, **only for reported letters**); Google AdMob once ads are enabled. Data may be processed in the United States.

**Your rights.** Edit your profile in Settings. Delete your account in Settings › Delete account or at https://bottlepost.web.app/delete-account. Other requests: kimyeoware@gmail.com.

**Minors.** The service is not directed at anyone under 18; accounts of minors are deleted.
