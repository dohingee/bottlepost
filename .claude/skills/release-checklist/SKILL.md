---
name: release-checklist
description: 보틀포스트 빌드·테스트·Play 출시 절차. 첫 출시, 업데이트 출시, targetSdk 연례 상향 때 사용.
---

# 출시 절차

## 1. 사전 검사
- `bash tools/policy-check.sh` (FAIL 0)
- `cd firebase/functions && npm test && npm run test:emulator`
- `cd firebase/rules-test && npm test` (보안 규칙)
- `cd android && ./gradlew testDebugUnitTest lint`

## 2. 서버 배포 (앱보다 먼저)
- `firebase deploy --only firestore:rules,firestore:indexes,functions,hosting`
- 처음 한 번: `firebase functions:secrets:set LETTER_HMAC_KEY` (임의의 긴 문자열), `OPENAI_API_KEY` (없으면 `disabled`)
- Firestore 콘솔에서 TTL 정책 활성 확인: deliveries.expireAt, reports.expireAt, bans.expireAt, appeals.expireAt

## 3. 앱 빌드
- `versionCode` +1, `versionName` 갱신 (`android/app/build.gradle.kts`)
- `./gradlew bundleRelease` → `.aab` (업로드 키로 서명, Play 앱 서명 사용)
- 출시 노트 8개 언어 (localization-agent)

## 4. Play Console
- 앱 콘텐츠: 개인정보처리방침 URL, 데이터 보안, 계정 삭제 URL, 타깃층 18+, 콘텐츠 등급, 아동 안전 표준 URL·담당자, 광고 포함 여부
- 첫 출시(개인 계정): 비공개 테스트 12명 이상 × 14일 → 프로덕션 신청
- 단계적 출시 20% → 이상 없으면 100%

## 5. 출시 후 1주
- Crashlytics 비정상 종료율 < 1%
- `npx tsx tools/matching-stats.ts`, `moderation-cli.ts queue` 매일 확인

## 연례: targetSdk 상향 (매년 8월 31일 마감)
1. Play 공지에서 새 요구 API 레벨 확인 → `compileSdk/targetSdk` 상향
2. 해당 Android 버전 동작 변경사항 검토 → 테스트
3. `tools/policy-check.sh`의 `MIN_TARGET_SDK` 갱신
