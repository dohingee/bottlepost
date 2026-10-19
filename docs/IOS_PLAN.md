# 보틀포스트 iOS 앱 — 계획

## 배경과 결정
- Android 앱(`android/`)과 같은 Firebase 백엔드(`bottlepost`, asia-northeast3)를 쓰는 iPhone 앱을 **네이티브 SwiftUI**로 `ios/`에 만든다. Android 코드는 건드리지 않는다.
- 운영자 환경: iPhone만 있음(Mac 없음), Apple Developer Program 가입 완료.
  - 빌드는 GitHub Actions macOS 러너에서 한다.
    - `ios-build.yml`: 시뮬레이터 빌드와 단위 테스트
    - `ios-testflight.yml`: 서명한 뒤 TestFlight에 업로드
  - 실기기 테스트는 TestFlight로 한다.
  - 비공개 저장소의 macOS 러너는 Linux보다 **10배** 빨리 무료 한도를 쓴다(무료 2,000분 ≈ macOS 200분). 그래서 `ios/`가 바뀔 때만 빌드하고, 같은 브랜치에서 이전 빌드가 돌고 있으면 취소한다.
- **불변식은 iOS에서도 같다** (`CLAUDE.md`).
  - 서버에 편지 본문을 보관하지 않는다.
    - `deliveries/{id}`는 수신 기기가 받은 즉시 삭제한다.
  - 상대 uid를 알 수 없다. 모든 것은 `threadId`로 주고받는다.
  - 모든 편지에 HMAC `sig`가 붙고, 신고할 때 `sig`도 함께 올린다.
  - 사용자 상태 변경은 모두 `enforcement.ts`를 거친다.
  - SDK나 수집 데이터를 추가하면 다음을 함께 갱신한다.
    - 개인정보처리방침
    - `POLICY_CHECKLIST.md` I절(App Privacy)
    - `policy-check.sh`

## 기술 구성
| 항목 | 선택 | 이유 |
|---|---|---|
| UI | SwiftUI, **iOS 18+**, iPhone 전용 | Translation API(iOS 18), `@Observable`. iPad 스크린샷·레이아웃 심사 부담 없음 |
| 프로젝트 | XcodeGen `ios/project.yml` (`.xcodeproj`는 생성물) | Mac 없이 Linux에서 편집 가능, 병합 충돌 없음 |
| 의존성 | Swift Package Manager만 | CocoaPods 불필요 |
| Firebase | Auth, Functions, AppCheck (이후 Firestore, Messaging, RemoteConfig) | Android와 같은 callable·규칙 재사용 |
| App Check | Debug: 디버그 공급자 / Release: **App Attest** | Play Integrity의 iOS 대응 |
| 로컬 저장 | SwiftData | Android Room 대응. 대화는 기기에만 |
| 번역 | **Apple Translation 프레임워크**(기기 내) | ML Kit iOS는 CocoaPods 전용. Apple 번역은 8개 언어 모두 지원하고 본문이 외부로 나가지 않음 |
| 나이 확인 | 중립적 출생연도 입력 + 서버 재검증 + **Declared Age Range**(iOS 26+) | 추가 비용·SDK·저장 데이터 없음. 아래 "18세 이상 확인" |
| 계정 연결 | **Sign in with Apple + Google** | 가이드라인 4.8. Google 연결로 Android↔iOS 계정 이동도 가능 |
| 백업 | **iCloud(CloudKit 비공개 DB)** 에 AES-GCM 암호문, 키는 `getBackupKey` | Drive appDataFolder 대응. iCloud만으로도, 서버만으로도 읽을 수 없음 |
| 푸시 | FCM + APNs 인증 키 | 서버 `push.ts` 그대로 |
| 문자열 | `tools/ios-strings.ts`가 Android `strings.xml`(8개 언어)에서 `Localizable.xcstrings` 생성 | 번역은 한 곳에서만 관리. 문구 추가는 Android 쪽에 하고 스크립트 실행 |
| 오류 코드 | `ios/Bottlepost/UI/Errors.swift` | `Errors.kt`와 같은 코드→문구 키 |
| 디자인 | `ios/Bottlepost/UI/Sea.swift` | `Sea.kt` 팔레트(밤바다 상단바, 종이 편지지, 밀랍 배지, 항공우편 카드) |

`GoogleService-Info.plist`는 커밋하지 않는다(google-services.json과 같음).
- TestFlight 워크플로가 서비스 계정으로 Firebase에서 받는다.
- 첫 실행이면 Firebase에 iOS 앱(`com.bottlepost.app`)을 등록한다.

## 18세 이상 확인 (세 겹)
1. **App Store 연령 등급 18+** (제출 때, M8)
   - 설문에 사실대로 답한다: 사용자 제작 콘텐츠 "예", 메시지·채팅 "예".
   - 계산된 등급이 18+보다 낮으면 18+로 올린다.
   - 이 등급은 자녀 보호 기능(스크린 타임)의 설치 차단에만 쓰이고, 나이를 확인해 주지는 않는다.
2. **앱 안의 중립적 나이 입력** (M3)
   - 출생연도를 빈 값으로 시작한다(기본 연도를 미리 고르지 않음).
   - 입력 전에 "18세 이상만" 같은 기준을 안내하지 않는다. "18세 이상입니다" 체크박스도 두지 않는다.
     - Android의 안내 문구·체크박스와 다르다. iOS 화면에는 쓰지 않는다.
   - 서버 `createProfile`이 다시 검증하고, 미만이면 `underage`를 돌려준다(Android와 공유, 이미 있음).
   - 미만으로 막히면 그 사실을 **키체인**에 기록한다.
     - 앱을 지웠다 다시 깔거나 다른 연도로 다시 시도해도 막힌다.
     - 키체인은 기기 안에만 있고 서버로 보내지 않는다.
3. **Apple Declared Age Range** (M3, iOS 26 이상 기기)
   - 온보딩에서 18세 기준으로 나이대 공유를 요청한다.
     - SwiftUI `requestAgeRange` 또는 `AgeRangeService`. 정확한 이름은 구현할 때 SDK로 확인한다.
   - 결과에 따라 이렇게 처리한다.
     - "18세 미만": 출생연도와 관계없이 차단하고 2번처럼 키체인에 기록한다.
     - "18세 이상": 출생연도 입력으로 진행한다. 출생연도가 미만이면 여전히 차단한다.
     - 공유 거절, iOS 18~25 기기: 출생연도만으로 판단한다.
   - 결과는 저장하지 않고 서버로 보내지 않는다.
     - App Privacy 답안이나 개인정보처리방침에 새로 적을 항목이 없다.
     - 비용도 없다(Apple 기본 기능).
   - 필요한 설정
     - App ID에 **Declared Age Range** 기능을 켠다.
     - 엔타이틀먼트(`com.apple.developer.declared-age-range`)를 추가한다.
   - 미국 일부 주(텍사스·유타·루이지애나 등)의 앱스토어 연령 확인 법은 시행·소송 상황이 바뀌고 있다. 제출 직전에 다시 확인한다.

## 서버 변경이 필요한 곳 (해당 마일스톤에서 함수 테스트와 함께)
- **Apple 연결**
  - `confirmGoogleLink`를 그대로 두고 `confirmAccountLink`를 추가한다.
    - `apple.com` 제공자를 받는다.
    - 밴 블랙리스트 해시는 Apple `sub` 기반으로 한다(숨긴 이메일 대응).
  - `getBackupKey`의 `linked` 조건은 그대로 쓴다.
  - 오류 코드 `google_link_required`는 유지하고, iOS 문구만 "계정 연결 필요"로 바꾼다.
- **계정 삭제**
  - Sign in with Apple로 연결된 계정이면 Apple 토큰 철회(`appleid.apple.com/auth/revoke`)를 한다.
  - 이 작업은 `enforcement.ts`가 아닌 `account.ts`에서 한다.
- **푸시**: `push.ts`는 FCM 토큰만 쓰므로 변경이 없다. APNs 키를 FCM에 올리면 된다.

## 마일스톤
| | 내용 | 상태 |
|---|---|---|
| M1 | 앱 골격: 익명 로그인 → `getProfile`, Sea 디자인, 문자열 카탈로그, 개인정보 매니페스트, CI 빌드·테스트, TestFlight 워크플로 | **완료** (CI 시뮬레이터 빌드·단위 테스트 8개 통과) |
| M2 | 운영자가 `IOS_RELEASE_GUIDE.md`대로 키·시크릿·App Attest·APNs를 등록 → 첫 TestFlight 업로드 → iPhone에서 `getProfile` 성공 확인 | **완료** (2026-10-19) |
| M3 | 온보딩(성별·출생연도·약관 동의) → `createProfile`<br>18세 확인: 중립적 출생연도 입력, Declared Age Range(iOS 26+), 미만이면 키체인 기록으로 재시도 차단<br>받은편지/인연 탭<br>쓰기(2~1,000자, 받을 성별)<br>스레드(교대 편지, 대기 배너·떠다니는 병, 나가기, 상대가 남아 있으면 이중 확인 삭제)<br>deliveries 수신·삭제, FCM 푸시 | **완료** (실기기 확인, 서버: APNs 알림 `pushMessage.ts`) |
| M4 | 신고(`sig` 포함, "미성년자" 사유)·차단·계정 삭제·이의 제기·문의하기 | **완료** (실기기 확인) |
| M5 | Apple Translation 번역 토글(받은 편지마다), 원문 언어는 기기 안에서 감지(NaturalLanguage), 대상은 편지 언어(`lang`) — Android와 같음. 앱 화면 언어는 iPhone 설정 > 보틀포스트 > 언어 | **완료** (실기기 확인) |
| M6 | Sign in with Apple 연결·복원(Google 없음, 사용자 결정), iCloud Drive 앱 전용 컨테이너에 AES-GCM 암호화 백업(하루 1회 자동 + 수동), 계정 삭제 시 Apple 토큰 철회·백업 삭제. 서버 `confirmAccountLink`(`identity.ts`) | **구현 완료** (사용자 설정: `IOS_RELEASE_GUIDE.md` "M6 준비") |
| M7 | AdMob(테스트 ID): 보내기 전 전면광고(빈도 제한, 2초 대기 후 생략), 메인 하단 앵커 배너, UMP → ATT 순서, SKAdNetwork 50개, Remote Config `ads_enabled`(기본 false) — Android와 같은 키 | **구현 완료** (실제 ID는 AdMob iOS 앱 승인 후) |
| M8 | 심사 제출: 스크린샷 8개 언어, App Privacy, 18+ 등급, 심사 메모, 단계적 출시 | |

**M1 범위 밖:** Android ↔ iOS 대화 이전.
- 백업 저장소가 Drive와 iCloud로 서로 다르기 때문이다.
- 계정(프로필·제재 상태)은 Google 연결로 옮겨지지만, 대화는 옮겨지지 않는다고 안내한다.

## App Store 심사 위험과 대응
| 가이드라인 | 위험 | 대응 |
|---|---|---|
| **1.2** 사용자 제작 콘텐츠 | "모르는 사람과 랜덤 익명 채팅" 앱은 자주 거절됨 | 필터링·신고·차단·연락처 공개를 모두 갖추고 심사 메모에 명시(아래 초안). 1:1 편지, 턴제, 하루 한도 → 실시간 채팅이 아님을 강조 |
| **4.8** 로그인 | Google 로그인만 있으면 거절 | Sign in with Apple을 같은 화면에, 같은 크기로 |
| **5.1.1(v)** 계정 삭제 | 앱 안에서 삭제 불가 시 거절 | 설정 > 계정 삭제(서버 `deleteAccount` 이미 있음) + Apple 토큰 철회 |
| **5.1.2** 추적 | ATT 없이 IDFA 사용 시 거절 | UMP 동의 후 ATT 요청, 거부 시 비개인화 광고 |
| **2.1** 완성도 | 심사자가 기능을 못 써 봄 | 로그인 불필요. 심사 중엔 매칭 상대가 없을 수 있어 운영자 테스트 계정이 답장하도록 준비 |
| 연령 등급·나이 확인 | 낮게 답하면 거절·삭제. 나이 입력이 기준을 미리 알려 주면 "우회 유도"로 봄 | 설문 사실대로 + 18+로 상향. 중립적 출생연도 입력 + Declared Age Range + 서버 재검증 ("18세 이상 확인") |
| 개인정보 | 라벨·매니페스트 불일치 | `POLICY_CHECKLIST.md` I절 표 = `PrivacyInfo.xcprivacy` |

### 심사 메모 초안 (App Review Information > Notes, 영어)
```
Bottlepost lets adults (18+) send one anonymous letter to a random adult; a conversation only
continues if the recipient replies, and each side must wait for the other's reply (turn-taking).
It is not a real-time chat: letters are limited per day and are delivered once.

Safety (Guideline 1.2):
- Terms of use with zero tolerance for objectionable content must be accepted before use.
- Before sending, a server filter blocks links, phone numbers, e-mails, messenger IDs and crypto.
- Every letter has Report (also blocks the sender immediately) and Block. Reports are analysed
  by a rules engine and the OpenAI moderation model and reviewed by the operator within 24 hours;
  child-safety reports suspend the sender at once.
- Age gate: birth year at onboarding, re-checked on the server.
- Support contact in Settings > Contact support; child-safety page: https://bottlepost.web.app/child-safety
- Account deletion: Settings > Delete account (also https://bottlepost.web.app/delete-account).

No login is needed to review the app. To receive a reply during review, send a letter choosing
"Anyone": our test account replies within a few minutes.
```

## 검증 방법
- 매 push: `ios-build.yml`이 시뮬레이터 빌드와 단위 테스트를 한다.
  - 테스트 대상: 프로필 디코딩, 오류 코드 매핑, 세션 상태, 문자열 존재 여부
- 로컬(Linux): `bash tools/policy-check.sh`(iOS 절), `cd tools && npx tsx ios-strings.ts --check`
- 실기기: TestFlight 빌드(App Attest)로 iPhone에서 확인한다.
  - 시뮬레이터에서 서버를 호출하려면 App Check 디버그 토큰을 등록해야 한다(`ios/README.md`).

## Android와 다르게 동작하는 부분 (의도적)
- **나이 확인**: iOS는 출생연도를 빈 칸으로 시작하고 "18세 이상" 안내·체크박스를 두지 않는다. 미만이면 키체인에 기록해 재시도를 막고, iOS 26+에서는 Apple Declared Age Range도 확인한다. Android는 기존 방식(안내 문구 + 체크박스) 그대로다.
- **앱 화면 언어**: iOS는 iPhone 설정 > 보틀포스트 > 언어(앱별 언어)를 따른다. 앱 안의 언어 선택은 편지 매칭·번역용 언어(`lang`)만 바꾼다.
- **푸시**: Android는 data 메시지를 받아 앱이 알림을 만들고, iOS는 서버가 보낸 APNs 알림(지역화 키, 편지 내용 없음)을 시스템이 표시한다. "상대가 나감" 알림은 둘 다 표시하지 않는다.
- **서버 오류 `no_recipient_available`·`service_paused`**: iOS는 해당 안내 문구를 보여 준다. Android는 이 둘을 `unavailable` 상태 때문에 "연결할 수 없어요"로 보여 주는 버그가 있다(`BottlepostApi.kt`의 offline 판정) — Android 세션에 전달 필요.
