# 보틀포스트 (Bottlepost)

받을 사람의 성별만 정해 낯선 이에게 편지를 띄우고, 답장이 오가면 인연이 되는 안드로이드 앱입니다.

- **로그인 없이 시작** (Firebase 익명 인증), 구글 계정을 연결하면 재설치·기기 변경 후에도 계정과 대화 유지
- **편지는 서버에 보관하지 않음**: 수신 기기에 도착하는 즉시 서버에서 삭제, 대화는 기기(Room DB)와 사용자 본인 구글 드라이브의 암호화 백업에만 저장
- **신고된 편지만 서버로**: 서명 검증 → 무료 규칙 엔진 + OpenAI Moderation(무료) → 자동 정지 / 사람 검토 / 기각
- **온디바이스 번역** (ML Kit, 무료, 본문이 밖으로 나가지 않음) + UI 8개 언어 (ko, en, ja, es, pt, id, vi, th)
- **광고 자리만 준비** (`AdGate`), AdMob 승인 후 연결

## 폴더 구조
```
android/            Kotlin + Jetpack Compose 앱 (targetSdk 36, minSdk 26)
ios/                SwiftUI 앱 (iOS 18+, XcodeGen) — 같은 Firebase 백엔드
firebase/
  functions/        Cloud Functions (TypeScript): 편지 중계, 매칭, 신고 봇, 계정
  firestore.rules   클라이언트는 자기 앞으로 온 편지 읽기·삭제만 가능
  hosting/          약관·개인정보·아동안전·계정삭제 웹페이지
  rules-test/       보안 규칙 테스트
tools/              운영 도구 8개 (신고 처리, 매칭 통계, 계정, 번역 검사, 정책 검사, 문의함, 광고 설정, 비용)
.claude/            운영용 Claude Code 에이전트 8개 + 스킬 8개
docs/               기획서, 정책 체크리스트, 운영 가이드, 앱 이름, 약관·개인정보 초안
```

## 처음 설정하기

### 1. Firebase
> **단계별 가이드: [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md)** — 서버 배포는 GitHub Actions(`.github/workflows/`)가 합니다. 아래는 직접 CLI로 할 때의 요약입니다.

1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트 생성 → **Blaze 요금제** + 예산 알림
2. Authentication: **익명**, **Google** 로그인 사용 설정
3. Firestore 생성 (위치: `asia-northeast3` 서울 권장. 바꾸면 `functions/src/config.ts`의 `REGION`과 앱의 `BottlepostApi.kt`도 변경)
4. App Check: Android 앱에 Play Integrity 등록
5. `firebase/.firebaserc`의 `YOUR_FIREBASE_PROJECT_ID` 교체
6. 비밀값 등록 후 배포:
   ```bash
   npm install -g firebase-tools && firebase login
   cd firebase/functions && npm install
   firebase functions:secrets:set LETTER_HMAC_KEY      # 임의의 긴 문자열
   firebase functions:secrets:set OPENAI_API_KEY       # platform.openai.com 키, 안 쓰면 disabled
   node ../scripts/build-legal.mjs                     # 약관 → HTML
   cd .. && firebase deploy
   ```
7. Firestore 콘솔 > TTL에서 `expireAt` 정책 4개가 활성인지 확인
8. **비용 자동 차단 설정** — [docs/OPERATIONS.md §7](docs/OPERATIONS.md) (예산 + Pub/Sub 연결 + 권한 1개)

### 2. Android
> **단계별 가이드: [docs/ANDROID_SETUP.md](docs/ANDROID_SETUP.md)** — 설치, 실행, App Check 디버그 토큰, 기기 2대로 편지 시험

1. Firebase 콘솔에서 Android 앱 추가 (패키지명: `com.bottlepost.app` → **본인 도메인으로 바꾸기 권장**, `app/build.gradle.kts`의 `namespace`/`applicationId`)
2. 디버그·릴리스 키의 **SHA-1** 등록 (구글 로그인에 필요)
3. `google-services.json`을 `android/app/`에 복사
4. `ui/common/Common.kt`의 `Links.BASE`, 지원 이메일 교체
5. Android Studio로 `android/` 열기 → 실행. 디버그 빌드는 Logcat에 App Check 디버그 토큰이 찍히니 Firebase 콘솔에 등록

### 3. iOS
> **계획: [docs/IOS_PLAN.md](docs/IOS_PLAN.md) · 출시(TestFlight) 가이드: [docs/IOS_RELEASE_GUIDE.md](docs/IOS_RELEASE_GUIDE.md)** — Mac 없이 GitHub Actions(macOS)로 빌드·업로드

### 4. 문서 마무리
- `docs/legal/*.md`의 `______`(운영자명, 이메일) 채우기 → `node firebase/scripts/build-legal.mjs` → `firebase deploy --only hosting`
- 출시 전 `bash tools/policy-check.sh` + `docs/POLICY_CHECKLIST.md`

## 테스트
```bash
cd firebase/functions && npm test              # 단위 테스트 (규칙 엔진, 판정, 매칭, 서명)
cd firebase/functions && npm run test:emulator # 전체 흐름 (가입 → 발송 → 수신 → 답장 → 신고 → 봇 → 삭제)
cd firebase/rules-test && npm install && npm test   # Firestore 보안 규칙
cd android && ./gradlew testDebugUnitTest lint
cd tools && npm install && npx tsx check-strings.ts && bash policy-check.sh
cd tools && npx tsx ios-strings.ts            # Android 문구 → iOS 문자열 카탈로그 (iOS 빌드·테스트는 CI: ios-build.yml)
```

## 운영
[docs/OPERATIONS.md](docs/OPERATIONS.md) — 준비물, 기능별 에이전트·스킬·도구, 신고 봇 구조, CSAM 대응, 일상 루틴.
