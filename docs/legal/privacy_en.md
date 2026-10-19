# Bottlepost Privacy Policy

> Effective date: October 18, 2026 · The [Korean version](https://bottlepost.web.app/privacy) has the full retention table and prevails in case of conflict.

**What we collect**
- Required: gender, birth year (to confirm you are 18+), app language/country, preferred recipient gender.
- Automatic: account ID (Firebase UID), push token, last-active time, send counters, conversation links (conversation ID and participant IDs — **never letter content**), crash logs, device-integrity verdicts.
- Ads (processed by Google AdMob): advertising ID, approximate location inferred from IP address, ad impressions and clicks, device information.
- Optional: nickname, up to 3 interest tags, Google account ID/email when you link Google.
- When you report: the reported letter, up to 5 preceding letters in that conversation, and the reason. Kept 90 days after resolution.
- When banned: account ID, a one-way hash of the linked Google email, and the reason, to prevent ban evasion (1 year after a temporary ban ends, 3 years for permanent bans).

**What we never collect:** real name, phone number, precise location, contacts, photos, hardware device identifiers.

**Letter content.** A letter waits on our relay (Google Cloud Firestore) only until it reaches the recipient's device and is deleted immediately afterwards; undelivered letters are deleted after 7 days. Conversations live on your device. Optional backups are encrypted and stored in your own Google Drive app-data folder, which we cannot access. Translation runs on-device (Google ML Kit on Android, Apple Translation on iPhone); the first use may download a language pack.

**Processors.** Google (Firebase/Google Cloud: hosting, auth, push, crash reporting, integrity); Apple (App Attest: device-integrity checks in the iPhone app; APNs: new-letter notifications, which never contain letter text); OpenAI (Moderation API, **only for reported letters**); Google AdMob (showing and measuring ads, ad fraud prevention). Data may be processed in the United States.

**iPhone app.** The iPhone app processes the same data for the same purposes and never stores letter content on our servers either. It checks that it is the genuine app with Apple App Attest instead of Google Play Integrity. It currently does not collect the advertising identifier (IDFA) or ask for tracking permission (App Tracking Transparency); if ads are added, this policy will be updated first and the app will ask for your permission.

**Ads.** An interstitial ad may appear occasionally when you send a letter (at least 3 minutes apart, with a daily cap), and a banner ad is shown at the bottom of the main screen (not while reading or writing letters). Letter content is never used for ads. Where consent is required (EEA, UK, Switzerland and similar), we ask before showing ads; change your choice any time in Settings › Ad privacy settings. Reset or delete your advertising ID in your phone's Settings › Google › Ads. How Google uses data: https://policies.google.com/technologies/partner-sites

**Your rights.** Edit your profile in Settings. Delete your account in Settings › Delete account or at https://bottlepost.web.app/delete-account. Other requests: kimyeoware@gmail.com.

**Minors.** The service is not directed at anyone under 18; accounts of minors are deleted.
