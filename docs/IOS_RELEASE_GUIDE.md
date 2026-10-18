# iOS 출시 가이드 (App Store / TestFlight)

Mac 없이 iPhone만으로 진행합니다. 순서대로 하세요.
- 빌드·서명·업로드는 GitHub Actions의 macOS 서버가 합니다.
- 설치와 테스트는 iPhone의 **TestFlight** 앱으로 합니다.

표시 안내:
- ✋ 표시는 **직접** 해야 하는 일입니다.
- 🤖 표시는 Claude/자동화가 하는 일입니다.
- 키 파일 내용과 비밀번호는 채팅에 붙이지 마세요. GitHub 비밀값 화면에만 넣습니다.

전체 흐름:
- 1~4단계: Apple 쪽 준비 (Team ID, 앱 ID, App Store Connect 앱, API 키)
- 5~6단계: GitHub에 키를 넣고 첫 빌드를 TestFlight에 올립니다.
- 7~8단계: Firebase에 App Attest와 푸시 키를 등록합니다.
- 9~10단계: iPhone에 설치해서 확인합니다.

계획과 마일스톤은 `docs/IOS_PLAN.md`, 심사 정책 점검은 `docs/POLICY_CHECKLIST.md` I절에 있습니다.

> 💰 **비용 안내**
> - 비공개 저장소에서 macOS 빌드 1분은 무료 한도에서 10분으로 계산됩니다.
> - 무료 한도는 월 2,000분이고, macOS로는 약 200분입니다.
> - 시뮬레이터 빌드(`iOS build`)는 `ios/` 폴더가 바뀔 때만 돌고, 한 번에 10~15분쯤 걸립니다.
> - GitHub → Settings → Billing에서 사용량을 확인할 수 있습니다.

---

## 1. ✋ 개발자 계정 정보 확인
1. https://developer.apple.com/account 에 로그인합니다.
2. **Membership details**(멤버십 세부사항)에서 **Team ID**(영문·숫자 10자리)를 메모합니다.
   - 5단계와 7단계에서 씁니다.
3. 개인(Individual) 계정은 App Store의 판매자 이름이 **본인 실명**으로 표시됩니다.
4. https://appstoreconnect.apple.com → **비즈니스**(계약, 세금 및 금융)를 엽니다.
   - "무료 앱" 계약이 **활성**인지 확인합니다.
   - 새 약관 동의 요청이 떠 있으면 동의합니다. 동의하지 않으면 업로드가 막힙니다.

## 2. ✋ 앱 ID(번들 ID) 등록
1. https://developer.apple.com/account/resources/identifiers/list 에서 **＋**를 누릅니다.
2. **App IDs** → **App** → Continue를 누릅니다.
3. 다음과 같이 입력합니다.
   - Description: `Bottlepost`
   - Bundle ID: **Explicit** → `com.bottlepost.app`
4. **Capabilities**에서 아래 3개를 체크합니다.
   - **App Attest**: 서버가 "진짜 보틀포스트 앱"인지 확인할 때 씁니다(Android의 Play Integrity와 같은 역할).
   - **Push Notifications**: 새 편지 알림에 씁니다.
   - **Sign in with Apple**: 계정 연결에 씁니다. 나중에 쓰지만 지금 켜 둡니다.
5. Continue → Register를 누릅니다.

> "이미 사용 중인 번들 ID"라고 나오면 Claude에게 알려 주세요.
> 🤖 Claude가 `ios/project.yml`과 워크플로의 번들 ID를 바꿉니다.

## 3. ✋ App Store Connect에 앱 만들기
1. https://appstoreconnect.apple.com → **앱** → **＋** → **신규 앱**을 누릅니다.
2. 다음과 같이 입력합니다.
   - 플랫폼: **iOS**
   - 이름: `보틀포스트`
     - 스토어 전체에서 중복될 수 없습니다.
     - 이미 있으면 `보틀포스트 - 바다에 띄운 편지`처럼 부제를 붙입니다.
   - 기본 언어: **한국어**
   - 번들 ID: 2단계의 `com.bottlepost.app` 선택
   - SKU: `bottlepost-ios` (내부 관리용, 아무 값이나 됩니다)
   - 사용자 액세스: **전체 액세스**
3. **생성**을 누릅니다. 스토어 정보와 심사 제출은 나중에(M8) 합니다.

## 4. ✋ App Store Connect API 키 만들기 (GitHub가 대신 업로드하기 위한 열쇠)
1. App Store Connect → **사용자 및 액세스** → **통합** 탭 → **App Store Connect API** → **팀 키**로 갑니다.
   - 처음이면 **API 액세스 요청** → 동의를 먼저 합니다.
2. **＋**(API 키 생성)를 누릅니다.
   - 이름: `GitHub Actions`
   - 액세스: **관리자(Admin)**
     - 서명 인증서를 자동으로 만들려면 관리자 권한이 필요합니다.
3. 생성된 줄에서 **API 키 다운로드**를 누르면 `AuthKey_XXXXXXXXXX.p8` 파일이 받아집니다.
   - **한 번만 받을 수 있습니다.** 비밀번호 관리자나 개인 클라우드에 보관하세요.
4. 같은 화면에서 두 값을 메모합니다.
   - **Issuer ID**: 표 위쪽에 있는 긴 UUID
   - **키 ID**: 10자리

## 5. ✋ GitHub 비밀값 등록
GitHub `dohingee/bottlepost` → **Settings** → **Secrets and variables** → **Actions**로 갑니다.

**Secrets** 탭 → **New repository secret**으로 3개를 만듭니다.

| Name | 넣을 값 |
|---|---|
| `ASC_KEY_ID` | 4-4의 키 ID |
| `ASC_ISSUER_ID` | 4-4의 Issuer ID |
| `ASC_KEY_P8` | 4-3의 `.p8` 파일 내용 전체. 메모장(또는 텍스트 편집기)으로 열어 `-----BEGIN PRIVATE KEY-----`부터 `-----END PRIVATE KEY-----`까지 모두 복사 |

**Variables** 탭 → **New repository variable**로 1개를 만듭니다.

| Name | 값 |
|---|---|
| `APPLE_TEAM_ID` | 1-2의 Team ID |

`FIREBASE_SERVICE_ACCOUNT`와 `FIREBASE_PROJECT_ID`는 이미 있습니다.

## 6. TestFlight에 첫 빌드 올리기
1. ✋ GitHub → **Actions** → 왼쪽 **iOS TestFlight** → **Run workflow** → 초록 버튼을 누릅니다.
   - Claude에게 "TestFlight 빌드 돌려 줘"라고 해도 됩니다.
2. 🤖 약 15~25분 동안 다음 일이 진행됩니다.
   - 처음 실행이면 Firebase에 iOS 앱을 등록하고, 설정 파일(GoogleService-Info.plist)을 받습니다.
   - 서명 인증서와 프로비저닝 프로파일을 자동으로 만듭니다.
   - 빌드한 뒤 App Store Connect에 업로드합니다.
   - 실패하면 실행 화면 위쪽에 원인이 표시됩니다(예: "GitHub secret ASC_KEY_P8 is not set"). Claude도 읽을 수 있습니다.
3. ✋ 업로드 후 5~30분 지나면 App Store Connect → 앱 → **TestFlight** 탭에 빌드가 "처리 완료"로 나타납니다.
   - 수출 규정(암호화) 질문은 앱에 이미 "면제"로 선언돼 있어 나오지 않습니다.

## 7. ✋ Firebase App Check에 App Attest 등록 (중요)
이 단계를 하지 않으면 iPhone 앱의 모든 서버 호출이 거절됩니다.

1. Firebase 콘솔(https://console.firebase.google.com/project/bottlepost) → ⚙️ **프로젝트 설정** → **내 앱**으로 갑니다.
   - 6단계에서 등록된 **Bottlepost iOS**(`com.bottlepost.app`)를 고릅니다.
2. **팀 ID** 칸에 1-2의 Team ID를 넣고 저장합니다.
3. 왼쪽 메뉴 **App Check** → **앱** 탭 → **Bottlepost iOS** → **App Attest** → **등록** → 저장합니다.
   - 토큰 유효 시간은 기본값 그대로 둡니다.

## 8. ✋ 푸시 알림 키(APNs) 등록
새 편지 알림 기능(M3)에 필요합니다. 미리 해 두면 됩니다.

1. https://developer.apple.com/account/resources/authkeys/list 에서 **＋**를 누릅니다.
2. 다음과 같이 설정합니다.
   - Key Name: `Bottlepost APNs`
   - **Apple Push Notifications service (APNs)** 체크 → **Configure**를 누릅니다.
     - Environment: **Sandbox & Production**
     - Key Restriction: **Team Scoped (All Topics)**
   - Save → Continue → Register를 누릅니다.
3. **Download**를 눌러 `.p8` 파일을 받습니다(한 번만 받을 수 있음). **Key ID**도 메모합니다.
4. Firebase 콘솔 → ⚙️ 프로젝트 설정 → **클라우드 메시징** 탭으로 갑니다.
   - **Apple 앱 구성 → Bottlepost iOS → APNs 인증 키 → 업로드**를 누릅니다.
   - `.p8` 파일, 키 ID, 팀 ID를 넣습니다.

## 9. ✋ iPhone에 설치 (TestFlight 내부 테스트, 심사 없음)
1. App Store Connect → 앱 → **TestFlight** → 왼쪽 **내부 테스트** 옆 **＋**를 누릅니다.
   - 그룹 이름은 `운영자`로 합니다.
2. 그룹 → **테스터** **＋**를 눌러 본인(App Store Connect 계정의 이메일)을 추가합니다.
   - **빌드** 탭에서 6단계의 빌드를 추가합니다.
3. iPhone에서 App Store의 **TestFlight** 앱을 설치합니다.
   - 같은 Apple 계정으로 로그인하면 보틀포스트가 보입니다.
   - 초대 이메일의 링크를 눌러도 됩니다.
   - **설치**를 누릅니다.
4. 내부 테스터는 App Store Connect 사용자만 가능합니다(최대 100명).
   - 친구 등 외부 테스터(공개 링크, 최대 10,000명)는 **외부 테스트** 그룹으로 초대합니다.
   - 외부 테스트는 첫 빌드에 Apple의 베타 심사(보통 1일)가 필요합니다.

## 10. ✋ 동작 확인
- 앱을 열면 밤바다 상단바 아래 종이 카드에 "낯선 누군가에게 유리병 편지를 띄워 보세요…" 소개 문구가 보입니다.
  - 이 문구가 보이면 성공입니다. 익명 로그인 → 서버(`getProfile`) 호출이 성공했고, 아직 프로필이 없다는 뜻입니다.
- "문제가 생겼어요"나 "연결할 수 없어요"가 나오면 7단계(App Check)를 확인하세요.
  - 그래도 안 되면 Claude에게 화면 문구를 알려 주세요.
- 온보딩, 편지 쓰기, 받은편지함은 다음 마일스톤(M3)부터 차례로 들어옵니다.

---

## 업데이트할 때마다
1. 🤖 Claude가 코드를 고쳐 push합니다. 그러면 **iOS build**가 시뮬레이터 빌드와 테스트를 자동으로 돌립니다.
2. ✋ 확인할 빌드가 필요할 때만 **iOS TestFlight**를 실행합니다.
   - 빌드 번호는 실행할 때마다 1씩 커집니다.
3. ✋ iPhone의 TestFlight 앱에서 **업데이트**를 누릅니다.

## 나중에: App Store 심사 제출 (M8)
`docs/IOS_PLAN.md` "App Store 심사 위험과 대응"과 `docs/POLICY_CHECKLIST.md` I절을 따라 다음을 채웁니다.
- 연령 등급 18+
- App Privacy
- 심사 메모(초안 있음)
- 스크린샷(6.9인치 iPhone)
- EU 거래자(DSA) 정보
