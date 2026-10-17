# Bottlepost

Anonymous "letter in a bottle" Android app. Kotlin/Compose client in `android/`, Firebase backend in `firebase/`, operator CLIs in `tools/`.

Invariants — keep them when changing code:
- Letter text is never stored server-side beyond delivery: `deliveries/{id}` is deleted by the recipient device on pickup, TTL 7 days. Only reported letters reach `reports/`.
- Clients never learn the other participant's uid; everything is addressed by `threadId`.
- Every letter carries an HMAC `sig` (`firebase/functions/src/signing.ts`); reports are verified with it.
- All user standing changes go through `firebase/functions/src/enforcement.ts` (bot, admin callables and CLIs share it). Modules imported by `tools/` must not import `firebase-functions`.
- Collecting new data or adding permissions/SDKs requires updating `docs/legal/privacy_*.md`, the Data safety table in `docs/POLICY_CHECKLIST.md`, and `tools/policy-check.sh`.
- Server error codes are HttpsError messages; map new ones in `android/.../ui/common/Errors.kt` and add strings in all 8 locales (`tools/check-strings.ts`).

Checks: `cd firebase/functions && npm test && npm run test:emulator`, `cd firebase/rules-test && npm test`, `bash tools/policy-check.sh`.
