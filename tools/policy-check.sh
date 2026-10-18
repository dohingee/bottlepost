#!/usr/bin/env bash
# Google Play pre-release check (automatable part of docs/POLICY_CHECKLIST.md).
#   bash tools/policy-check.sh        exit code 1 when something must be fixed
set -u
cd "$(dirname "$0")/.."
APP=android/app
fail=0
ok()   { echo "  ok   $1"; }
bad()  { echo "  FAIL $1"; fail=1; }
warn() { echo "  warn $1"; }

MIN_TARGET_SDK=36   # Play requirement for new apps/updates from 2026-08-31; raise every August.

echo "== Build configuration"
target=$(grep -oE 'targetSdk *= *[0-9]+' $APP/build.gradle.kts | grep -oE '[0-9]+')
compile=$(grep -oE 'compileSdk *= *[0-9]+' $APP/build.gradle.kts | grep -oE '[0-9]+')
min=$(grep -oE 'minSdk *= *[0-9]+' $APP/build.gradle.kts | grep -oE '[0-9]+')
[ "${target:-0}" -ge $MIN_TARGET_SDK ] && ok "targetSdk $target" || bad "targetSdk ${target:-?} < $MIN_TARGET_SDK"
[ "${compile:-0}" -ge "${target:-0}" ] && ok "compileSdk $compile" || bad "compileSdk ${compile:-?} < targetSdk"
[ "${min:-0}" -ge 23 ] && ok "minSdk $min" || warn "minSdk ${min:-?} is very old"
grep -q 'isMinifyEnabled = true' $APP/build.gradle.kts && ok "release minify enabled" || bad "release build not minified"
grep -q 'com.android.application' gradle/libs.versions.toml 2>/dev/null || true

echo "== Manifest"
M=$APP/src/main/AndroidManifest.xml
# Prefer the merged manifest (includes permissions added by libraries) when a build exists.
MERGED=$(ls $APP/build/intermediates/merged_manifests/release/*/AndroidManifest.xml $APP/build/intermediates/merged_manifest/release/*/AndroidManifest.xml 2>/dev/null | head -1)
PM=${MERGED:-$M}
[ -n "$MERGED" ] && ok "checking merged release manifest" || warn "no release build yet: checking source manifest only (run ./gradlew assembleRelease)"
# Normal-protection permissions added by Firebase/WorkManager are expected; declare them in Data safety if asked.
# AdMob adds AD_ID and the Privacy Sandbox (ADSERVICES) permissions; both are in Data safety (POLICY_CHECKLIST E).
allowed="com.google.android.gms.permission.AD_ID android.permission.ACCESS_ADSERVICES_AD_ID android.permission.ACCESS_ADSERVICES_ATTRIBUTION android.permission.ACCESS_ADSERVICES_TOPICS android.permission.INTERNET android.permission.POST_NOTIFICATIONS android.permission.ACCESS_NETWORK_STATE android.permission.WAKE_LOCK android.permission.RECEIVE_BOOT_COMPLETED com.google.android.c2dm.permission.RECEIVE com.bottlepost.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION com.google.android.providers.gsf.permission.READ_GSERVICES"
for p in $(grep -v 'tools:node="remove"' $PM | grep -oE 'uses-permission android:name="[^"]+"' | sed -E 's/.*"([^"]+)"/\1/' | sort -u); do
  case " $allowed " in *" $p "*) ok "permission $p";; *) bad "unexpected permission $p (update Data safety + checklist first)";; esac
done
grep -q 'android:allowBackup="false"' $M && ok "allowBackup=false" || bad "allowBackup must be false (backups go to Drive appData only)"
grep -q 'usesCleartextTraffic="true"' $M && bad "cleartext traffic enabled" || ok "no cleartext traffic"
grep -q 'AD_ID' $PM && warn "AD_ID permission present: Play Console > App content > Advertising ID must say Yes" || ok "no AD_ID"
grep -q 'ca-app-pub-3940256099942544' $APP/build.gradle.kts && grep -A3 'release {' $APP/build.gradle.kts | grep -q '3940256099942544' && bad "release build uses AdMob test IDs" || ok "release AdMob IDs are not the test IDs"
[ -f firebase/hosting/app-ads.txt ] && ok "hosting/app-ads.txt" || bad "missing firebase/hosting/app-ads.txt"

echo "== Required public pages"
for f in terms privacy child-safety delete-account; do
  [ -f firebase/hosting/$f.html ] && ok "hosting/$f.html" || bad "missing firebase/hosting/$f.html (run node firebase/scripts/build-legal.mjs)"
done
grep -q '______' docs/legal/terms_ko.md && warn "legal drafts still contain ______ placeholders (operator name, e-mails)"
grep -q 'YOUR_PROJECT' $APP/src/main/java/com/bottlepost/app/ui/common/Common.kt && warn "Links.BASE still points to YOUR_PROJECT"

echo "== Safety features (UGC + Child Safety Standards policies)"
grep -rq '"minor"' $APP/src/main/java && ok "in-app report has a minor-safety reason" || bad "no minor-safety report reason"
grep -rq 'fun block' $APP/src/main/java && ok "block user" || bad "no block feature"
grep -rq 'deleteAccount' $APP/src/main/java && ok "in-app account deletion" || bad "no in-app account deletion"
grep -q 'enforceAppCheck' firebase/functions/src/common.ts && ok "App Check enforced on callables" || bad "App Check not enforced"

echo "== Localization"
if command -v npx >/dev/null && [ -d tools/node_modules ]; then
  (cd tools && npx tsx check-strings.ts >/dev/null) && ok "strings complete in all locales" || bad "string resources incomplete (npx tsx tools/check-strings.ts)"
else
  warn "run 'npm install' in tools/ to enable the strings check"
fi

echo
[ $fail -eq 0 ] && echo "Policy check passed. Now go through the manual items in docs/POLICY_CHECKLIST.md." || echo "Policy check FAILED."
exit $fail
