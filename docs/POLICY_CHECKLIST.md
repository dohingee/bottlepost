# Google Play 정책 준수 체크리스트

출시 전에 `tools/policy-check.sh`(자동)와 이 문서(수동)를 둘 다 통과해야 합니다. 정책은 자주 바뀌므로 출시 직전 Play Console 공지와 [Play 정책 센터](https://play.google.com/about/developer-content-policy/)를 다시 확인하세요.

## A. 기술 요구사항 (자동 검사: `tools/policy-check.sh`)
- [ ] `targetSdk = 36`, `compileSdk = 36` (2026-08-31 이후 신규 앱·업데이트 기준. 매년 8월 말 상향되므로 매년 갱신)
- [ ] `minSdk = 26`
- [ ] 앱이 요청하는 권한은 `INTERNET`, `POST_NOTIFICATIONS`뿐 (위치·연락처·전화·저장소 없음). 라이브러리가 추가하는 일반 권한(네트워크 상태, WAKE_LOCK, 부팅 완료, FCM 수신)만 허용, 생체인식·포그라운드 서비스는 매니페스트에서 제거
- [ ] `android:allowBackup="false"` — 대화 백업은 Drive appDataFolder로만 처리(의도치 않은 Auto Backup 방지)
- [ ] `usesCleartextTraffic` 미사용
- [ ] 릴리스 빌드 `isMinifyEnabled = true`
- [ ] 16KB 메모리 페이지 크기 호환 (네이티브 라이브러리 포함 시 AGP 8.5.1+ 로 빌드, Play Console App Bundle Explorer에서 확인)
- [ ] edge-to-edge (`enableEdgeToEdge()`), predictive back 지원
- [ ] Android App Bundle(.aab)로 업로드, Play 앱 서명 사용

## B. 사용자 제작 콘텐츠(UGC) 정책
- [ ] 첫 실행 시 이용약관·개인정보처리방침 동의 (금지 콘텐츠 명시) — `OnboardingScreen`
- [ ] 모든 편지에 신고 기능 — `ThreadScreen` 메뉴 > 신고
- [ ] 사용자 차단 기능 — `ThreadScreen` 메뉴 > 차단
- [ ] 지속적인 모더레이션 — `onReportCreated` 파이프라인 + 관리자 검토 큐
- [ ] 발송 전 필터(링크·연락처 차단) — `functions/src/moderation/rules.ts`

## C. 아동 안전 표준(CSAE) 정책 — 소셜/데이팅 앱 필수
- [ ] 아동안전 표준 페이지 공개 게시 (`docs/legal/child_safety.md` → `https://<도메인>/child-safety`)
- [ ] Play Console > 앱 콘텐츠 > 아동 안전 표준에 URL과 **담당자 연락처** 등록
- [ ] 인앱 신고에 "미성년자 관련" 사유 포함 — 즉시 정지 처리
- [ ] CSAM 발견 시 관계 기관 신고 절차 (`docs/OPERATIONS.md` §4)

## D. 대상 연령·콘텐츠 등급
- [ ] 앱 콘텐츠 > 타겟층: **18세 이상만** 선택 (가족 정책 대상 아님)
- [ ] 온보딩 출생연도 입력으로 만 18세 미만 차단 (서버 `createProfile`에서도 재검증)
- [ ] IARC 콘텐츠 등급 설문: "사용자 간 상호작용 있음", "사용자 제작 콘텐츠 있음"으로 정확히 응답
- [ ] 앱 카테고리: 소셜 또는 데이팅

## E. 개인정보·데이터 보안
- [ ] 개인정보처리방침 URL 등록 (앱 내 + 스토어)
- [ ] **데이터 보안 양식**: 아래 표와 실제 동작 일치
- [ ] **계정 삭제**: 앱 내(`설정 > 계정 삭제`) + 웹 URL(`https://<도메인>/delete-account`) 모두 등록
- [ ] 광고 ID 사용 선언: Play Console > 앱 콘텐츠 > 광고 ID "예" — 목적: 광고 또는 마케팅, 분석 (AdMob SDK가 `AD_ID` 권한을 병합)

| 데이터 유형 (Play 분류) | 수집 | 공유 | 필수/선택 | 목적 |
|---|---|---|---|---|
| 개인 정보 > 기타 정보 (성별, 출생연도) | 예 | 아니요 | 필수 | 앱 기능, 계정 관리 |
| 개인 정보 > 이메일 주소 (Google 연결 시) | 예 | 아니요 | 선택 | 계정 관리 |
| 메시지 > 기타 인앱 메시지 | 예 (전송 중 일시 저장, 신고 시 보관) | 예 (신고 건만 OpenAI) | 필수 | 앱 기능, 사기 방지·보안 |
| 앱 활동 > 기타 작업 (발송 횟수 등) | 예 | 아니요 | 필수 | 사기 방지·보안 |
| 앱 정보 및 성능 > 비정상 종료 로그 | 예 | 아니요 | 필수 | 분석 |
| 기기 또는 기타 ID (Firebase 설치 ID, 푸시 토큰, **광고 ID**) | 예 | 예 (광고 ID만 Google AdMob) | 필수 | 앱 기능, 광고 또는 마케팅, 분석 |
| 위치 > 대략적인 위치 (IP 기반, AdMob) | 예 | 예 (Google AdMob) | 필수 | 광고 또는 마케팅 |
| 앱 활동 > 앱 상호작용 (광고 노출·클릭, AdMob) | 예 | 예 (Google AdMob) | 필수 | 광고 또는 마케팅, 분석 |
| 앱 정보 및 성능 > 진단 (AdMob SDK) | 예 | 예 (Google AdMob) | 필수 | 광고 또는 마케팅, 분석 |

- 전송 중 암호화: 예 / 삭제 요청 가능: 예

## F. 계정·인증
- [ ] Google 로그인은 Credential Manager(`androidx.credentials`) 사용 (구 GoogleSignIn API는 지원 중단)
- [ ] Drive 스코프는 `drive.appdata`만 요청 (사용자 파일 접근 없음)
- [ ] 로그인 없이 핵심 기능 사용 가능 (요구사항 3) — 리뷰어용 테스트 계정 불필요하지만 "앱 액세스" 항목에 설명 기재

## G. 광고 (AdMob, `ads/AdMobAdGate.kt`)
- [x] `AdMobAdGate` 연결. 디버그 빌드는 Google 테스트 광고 ID만 사용
- [x] 하단 배너(`ads/AdBanner.kt`): 메인 화면에만, 탭 막대 아래 구분선으로 분리. 편지 읽기·쓰기 화면에는 없음
- [x] 배너 광고 단위 `ca-app-pub-7472495816861016/1458365900` (release `ADMOB_BANNER`)
- [ ] 출시 빌드 광고는 Remote Config `ads_enabled`(기본 false). AdMob 앱 검토 승인 후 Firebase 콘솔에서 true
- [ ] Play Console > 앱 콘텐츠 > 광고: "예, 광고가 있습니다"
- [ ] AdMob 차단 관리: 광고 콘텐츠 등급 최대 T/PG, 데이트·성인·도박 카테고리 차단
- [ ] 전면광고는 "보내기" 버튼을 누른 뒤 자연스러운 전환 시점에만, 빈도 제한(예: 3분에 1회)
- [ ] 앱 실행 직후·종료 시 전면광고 금지, 실수 클릭 유도 배치 금지
- [ ] EEA/UK/스위스 사용자 대상 UMP(사용자 메시지 플랫폼) 동의 SDK
- [ ] `app-ads.txt` 도메인 게시 (`firebase/hosting/app-ads.txt`), 스토어 등록정보 웹사이트 = `https://bottlepost.web.app`

## H. 출시 절차
단계별 안내: `docs/RELEASE_GUIDE.md` (업로드 키, GitHub Actions 빌드, Play Console 설문 답안, Play Integrity 등록)
- [ ] 개인 개발자 계정: **비공개 테스트 12명 이상 × 연속 14일** 후 프로덕션 신청
- [ ] 개발자 신원 확인(신분증·연락처) 완료
- [ ] 스토어 등록정보: 스크린샷에 실제 사용자 개인정보 노출 금지, "만남 보장" 등 과장 문구 금지

## I. App Store (iOS 앱, `ios/`)
계획·마일스톤: `docs/IOS_PLAN.md`, 출시 절차: `docs/IOS_RELEASE_GUIDE.md`. 자동 검사는 `tools/policy-check.sh`의 "iOS" 절입니다. 가이드라인 원문은 [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)에서 제출 직전에 다시 확인하세요.

**기술 요구사항**
- [ ] 최신 Xcode·iOS SDK로 빌드 (2026년 4월 말부터 Xcode 26 / iOS 26 SDK 필수. 매년 봄 상향되므로 제출 전 Apple 공지 확인. CI `macos-26` 러너의 Xcode 버전을 실행 요약에서 확인)
- [x] 개인정보 매니페스트 `PrivacyInfo.xcprivacy` — 아래 App Privacy 답안과 일치
- [x] `ITSAppUsesNonExemptEncryption = false` (HTTPS와 Apple 기본 암호화만 사용 → 수출 규정 서류 면제)
- [x] 권한 요청 문구(`NS…UsageDescription`)는 실제로 쓰는 것만. 현재 없음. ATT(`NSUserTrackingUsageDescription`)는 광고 도입 때 추가
- [x] App Check = App Attest (엔타이틀먼트 + Firebase 콘솔 등록)
- [ ] 비공개 API·숨은 기능·원격 코드 다운로드 없음 (2.5.2, 2.3.1)

**1.2 사용자 제작 콘텐츠 — 익명 랜덤 채팅 앱이 가장 많이 거절되는 항목**
- [x] 불쾌한 콘텐츠 필터링: 발송 전 링크·연락처·코인 필터(`moderation/rules.ts`) + 신고 시 규칙 엔진·OpenAI 모더레이션 + 관리자 검토
- [x] 신고 기능과 신속한 대응: 모든 편지에 신고(신고=즉시 차단), 미성년자 관련은 즉시 정지 — iOS `UI/Thread/ReportSheet.swift`
- [x] 사용자 차단 기능 (대화 메뉴 > 차단)
- [x] 연락처 공개: 앱 내 "문의하기" + 지원 URL
- [x] 이용약관(EULA) 동의 후 이용, 금지 행위에 무관용 명시 (온보딩)
- [ ] 18+ 확인(출생연도, 서버 재검증), 턴제(답장 전 재발송 불가), 일일 발송 한도 — 심사 메모에 강조

**18세 이상 확인** (설계: `docs/IOS_PLAN.md` "18세 이상 확인")
- [ ] 연령 등급 **18+**: 설문에 사실대로 답하고, 계산된 등급이 낮으면 18+로 올림
- [x] 중립적 나이 입력 (`ios/Bottlepost/UI/Onboarding/OnboardingView.swift`)
  - 출생연도 기본값 없음
  - 18세 기준을 입력 전에 안내하지 않음
  - "18세 이상입니다" 체크박스 대신 입력값으로 판정
- [x] 미만 입력 시 키체인에 기록 → 재설치·다른 연도로 재시도해도 차단
- [x] 서버 재검증 `createProfile` → `underage`
- [x] **Declared Age Range**(iOS 26+): 18세 기준으로 요청
  - 미만 → 차단
  - 거절·미지원 → 출생연도로만 판단
  - 결과는 저장·전송하지 않음 → App Privacy 변경 없음
- [x] App ID의 Declared Age Range 기능 + 엔타이틀먼트 `com.apple.developer.declared-age-range`
- [ ] 미국 주별 앱스토어 연령 확인 법(텍사스·유타·루이지애나 등) 시행 상황을 제출 직전에 확인

**알림 (4.5.4)**
- [x] 알림 권한은 메인 화면에서 한 번만 요청. 거절해도 모든 기능 사용 가능
- [x] 알림에 편지 내용 없음 (서버 `pushMessage.ts`의 APNs 지역화 키만 사용), 광고·마케팅 알림 없음

**계정 (4.8, 5.1.1)**
- [x] 로그인 없이 핵심 기능 사용 가능 (익명 계정)
- [x] Google 로그인을 제공하면 **Sign in with Apple**도 같은 화면에 제공 (4.8) — iOS는 Apple만 제공하므로 해당 없음
- [x] 앱 내 계정 삭제 (5.1.1(v)) — 설정 > 계정 삭제(`deleteAccount`), 확인 후 기기 데이터도 삭제. Sign in with Apple 연결 계정은 Apple 재인증 후 토큰 철회(`Auth.revokeToken`, Firebase Apple 제공업체의 OAuth 코드 흐름 구성 필요)와 iCloud 백업 삭제
- [ ] 계정 삭제 웹 URL `https://bottlepost.web.app/delete-account`

**광고 (5.1.2)** — `ios/Bottlepost/Ads/AdsManager.swift`
- [x] UMP 동의 → **ATT 허용 요청** → 광고 요청 순서. 거부해도 앱 사용 가능, 거부 시 IDFA 미사용
- [x] `SKAdNetworkItems`(Google 목록 50개) Info.plist 등록
- [x] `PrivacyInfo.xcprivacy`의 `NSPrivacyTracking` = true (SDK 매니페스트가 도메인·데이터 선언)
- [x] 보내기 전 전면광고(빈도 제한·2초 안에 없으면 생략), 메인 화면 하단 배너만. 편지 읽기·쓰기 화면엔 배너 없음
- [ ] AdMob에 iOS 앱 등록 → 실제 앱 ID·광고 단위 ID를 `ios/project.yml`에 넣기(지금은 테스트 ID) → 승인 후 Remote Config `ads_enabled` = true
- [ ] App Privacy에 광고 데이터(추적 포함) 답변 — 아래 표의 AdMob 줄

**App Store Connect 답안**
- 연령 등급: 설문에서 "사용자 생성 콘텐츠", "메시지·채팅"을 정확히 체크 → **18+**. 계산 결과가 낮으면 18+로 올림
- 카테고리: 소셜 네트워킹
- 개인정보처리방침 URL `https://bottlepost.web.app/privacy` / 지원 URL `https://bottlepost.web.app`
- EU 디지털 서비스법(DSA) 거래자 여부: 광고 수익이 있으면 "거래자" → 주소·전화번호·이메일이 EU 스토어에 공개됨. 공개를 원치 않으면 EU 배포 제외 검토
- App Privacy (데이터 수집 답안). "추적에 사용"은 모두 아니요(광고 도입 전)

| Apple 데이터 유형 | 사용자와 연결 | 목적 | 비고 |
|---|---|---|---|
| 식별자 > 사용자 ID (Firebase UID) | 예 | 앱 기능 | |
| 사용자 콘텐츠 > 기타 사용자 콘텐츠 (편지 중계, 신고된 편지) | 예 | 앱 기능 | 전달 즉시 삭제, 신고 건만 보관 |
| 기타 데이터 (성별, 출생연도, 언어·국가) | 예 | 앱 기능 | |
| 연락처 정보 > 이메일 주소 | — | — | **수집 안 함**: iOS는 Sign in with Apple만 쓰고 이메일·이름 scope를 요청하지 않음. Apple 식별자는 해시로만 보관(사용자 ID 항목에 포함) |
| 진단 > 충돌 데이터 | 아니요 | 앱 기능 | Crashlytics 도입 시 추가 |
| 식별자 > 기기 ID (IDFA), 위치 > 대략적 위치, 사용 데이터 > 광고 데이터 | 예 | 타사 광고 (추적: ATT 허용 시 예) | AdMob 도입 시 추가 |
