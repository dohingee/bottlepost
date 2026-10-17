# Firebase 프로젝트 설정 가이드

서버 배포는 GitHub Actions가 합니다. 아래 콘솔 작업만 직접 하시면 되고, 각 단계가 끝나면 Claude에게 알려 주세요. 배포 결과 확인과 오류 수정은 Claude가 합니다.

> 서비스 계정 키(JSON)는 **GitHub 비밀값에만** 넣으세요. 채팅이나 저장소 파일에 붙여넣지 마세요.

## 1단계. 프로젝트 만들기 (5분)
- [ ] https://console.firebase.google.com → **프로젝트 추가**
  - 이름: `bottlepost` (프로젝트 ID는 자동으로 `bottlepost-xxxxx` 형태가 됨 — **이 ID를 메모**)
  - Google 애널리틱스: **사용 안 함** (수집 데이터를 줄여 개인정보 안내가 단순해짐)
- [ ] 왼쪽 아래 **업그레이드** → **Blaze** 선택 → 결제 계정 연결
  - "예산 설정" 화면이 나오면 일단 건너뛰기 (5단계에서 자동 차단과 함께 설정)

## 2단계. 서비스 켜기 (5분)
- [ ] **Firestore Database** → 데이터베이스 만들기
  - 버전: Standard / 위치: **asia-northeast3 (서울)** / **프로덕션 모드**
  - 위치는 나중에 바꿀 수 없음
- [ ] **Authentication** → 시작하기 → 로그인 방법
  - **익명** 사용 설정
  - **Google** 사용 설정 (공개용 이름 `Bottlepost`, 프로젝트 지원 이메일 선택)
  - Google 로그인을 켠 **다음에** 3단계에서 `google-services.json`을 받아야 함 (로그인용 클라이언트 ID가 그때 들어감)
  - "Identity Platform으로 업그레이드" 안내는 무시
- [ ] Google Cloud 콘솔 → **Google 인증 플랫폼(OAuth 동의 화면)** → 대상(Audience)에서 게시 상태가 **프로덕션 단계**인지 확인 (테스트 단계면 등록한 테스트 사용자만 로그인 가능)
- [ ] **Hosting** (App Hosting 아님) → 시작하기 → 안내 화면은 모두 "다음"으로 넘기고 완료 (CLI 설치 안내는 무시)
- [ ] 프로젝트 설정(톱니바퀴) → 내 앱 → **웹 앱 추가** (`</>` 아이콘)
  - 앱 닉네임: `bottlepost-web`
  - **"이 앱의 Firebase 호스팅도 설정합니다" 체크** → 사이트 목록에서 기본 사이트(프로젝트 ID) 선택
  - 계정 삭제 웹페이지가 이 연결로 Firebase 설정을 불러옴. 화면의 설정 코드·CLI 안내는 따라 할 필요 없음

## 3단계. 안드로이드 앱 등록 (5분)
- [ ] 프로젝트 설정 → 내 앱 → **Android 앱 추가**
  - 패키지 이름: `com.bottlepost.app`
  - 디버그 서명 인증서 SHA-1: Android Studio에서 프로젝트를 열고 오른쪽 Gradle 탭 → `app > Tasks > android > signingReport` 실행 → `Variant: debug`의 SHA1 복사
    (터미널에서는 `cd android && ./gradlew signingReport`)
- [ ] `google-services.json` 다운로드 → 본인 PC의 `android/app/` 폴더에 저장
  - 저장소에는 올라가지 않게 설정되어 있음 (`.gitignore`)
  - 나머지 SDK 추가 안내는 이미 코드에 반영되어 있으니 "다음"으로 넘기기

## 4단계. 배포용 서비스 계정 + GitHub 연결 (10분)
- [ ] https://console.cloud.google.com/iam-admin/serviceaccounts?project=프로젝트ID → **서비스 계정 만들기**
  - 이름: `github-deployer`
  - 역할 6개 추가: **편집자**, **Cloud Functions 관리자**, **Cloud Run 관리자**, **Secret Manager 관리자**, **서비스 계정 사용자**, **프로젝트 IAM 관리자**
    (Cloud Run 관리자: 2세대 함수가 Cloud Run 위에서 돌아서, 앱이 함수를 호출하도록 공개 설정할 때 필요. 검색이 안 되면 영어 이름 `Cloud Run Admin`)
    (프로젝트 IAM 관리자: 첫 배포 때 Google 내부 서비스 계정(Pub/Sub, Eventarc)에 필요한 권한을 연결. 영어 이름 `Project IAM Admin`)
  - 세 번째 화면(사용자 액세스 권한)은 비워 두고 완료
- [ ] 만든 계정 클릭 → **키** 탭 → 키 추가 → 새 키 만들기 → **JSON** → 파일이 다운로드됨
- [ ] https://github.com/dohingee/bottlepost/settings/secrets/actions
  - **Secrets** 탭 → New repository secret
    - `FIREBASE_SERVICE_ACCOUNT` = 다운로드한 JSON 파일 내용 전체
    - `OPENAI_API_KEY` = https://platform.openai.com/api-keys 에서 만든 키 (무료 Moderation용)
  - **Variables** 탭 → New repository variable
    - `FIREBASE_PROJECT_ID` = 1단계에서 메모한 프로젝트 ID (이름 `bottlepost`가 아니라 `bottlepost-ab12c` 같은 **ID**)
- [ ] 다운로드한 JSON 키 파일은 GitHub에 등록한 뒤 PC에서 **삭제**

## 5단계. 배포 (Claude와 함께)
- [ ] https://github.com/dohingee/bottlepost/actions → **Firebase setup** → Run workflow
- [ ] 끝나면 **Firebase deploy** → Run workflow (첫 배포는 5~10분)
- [ ] Claude에게 "실행했어"라고 알리기 → 결과 확인·오류 수정
  - 첫 배포에서 "Eventarc Service Agent" 권한 오류가 나면 몇 분 뒤 다시 실행하면 됩니다 (Google 쪽 권한 전파 지연)

## 6단계. 비용 자동 차단 (10분, 배포 후)
- [ ] https://console.cloud.google.com/billing/budgets → 예산 만들기
  - 범위: 이 프로젝트 / 금액: **월 5,000원** (결제 계정 통화가 USD면 **$4**)
  - 알림 기준: 50%, 90%, 100%
  - **"Pub/Sub 주제를 이 예산에 연결"** 체크 → 주제 `billing-budget` 선택
- [ ] 결제 > 계정 관리 > 오른쪽 **권한** → 주 구성원 추가
  - `프로젝트번호-compute@developer.gserviceaccount.com` (프로젝트 번호는 Firebase 프로젝트 설정에 있음)
  - 역할: **결제 계정 관리자**
- [ ] Firestore → **TTL** 탭에 `expireAt` 정책 4개(deliveries, reports, bans, appeals)가 "제공됨"인지 확인

## 7단계. 마무리 (Claude가 코드 반영)
- 프로젝트 ID를 알려 주시면 `.firebaserc`와 앱의 웹페이지 주소(`https://프로젝트ID.web.app`)를 바꿔 드립니다.
- 앱 실행 시 App Check 디버그 토큰 등록 방법은 README "Android" 절 참고.
