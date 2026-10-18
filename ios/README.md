# Bottlepost iOS

SwiftUI app (iOS 18+, iPhone) on the same Firebase backend as `android/`. Plan: [docs/IOS_PLAN.md](../docs/IOS_PLAN.md), release steps: [docs/IOS_RELEASE_GUIDE.md](../docs/IOS_RELEASE_GUIDE.md).

```
project.yml            XcodeGen spec (the .xcodeproj, Info.plist and entitlements are generated)
Bottlepost/App         entry point, App Check setup (debug provider in Debug, App Attest in Release)
Bottlepost/Data        callable wrapper (BottlepostService), Profile, Session
Bottlepost/UI          screens, Sea palette (= Sea.kt), server error codes (= Errors.kt)
Bottlepost/Resources   assets, PrivacyInfo.xcprivacy, string catalogs (generated, see below)
BottlepostTests        unit tests
```

- **Builds run in CI**: `.github/workflows/ios-build.yml` builds for the simulator and runs the tests on every
  change under `ios/`; `ios-testflight.yml` signs and uploads to TestFlight (manual).
- **Strings**: do not edit `*.xcstrings`. Add or change text in `android/app/src/main/res/values*/strings.xml`
  (all 8 locales), then run `cd tools && npx tsx ios-strings.ts`. Use it in Swift as `L("key")`.
- **Firebase config**: `GoogleService-Info.plist` is not committed; CI fetches it. Locally on a Mac:
  `npx firebase-tools apps:sdkconfig IOS --project bottlepost --out Bottlepost/GoogleService-Info.plist`.
- **On a Mac** (optional): `brew install xcodegen && xcodegen generate && open Bottlepost.xcodeproj`.
  Debug builds print an App Check debug token in the Xcode console; add it in Firebase console >
  App Check > Apps > Bottlepost iOS > Manage debug tokens, or every call fails with App Check errors.
