# Android Studio 설치와 앱 실행

서버(Firebase 프로젝트 `bottlepost`)는 이미 배포되어 있다는 전제입니다. 처음엔 1~2시간(대부분 다운로드) 걸립니다.

## 1. Android Studio 설치
- [ ] https://developer.android.com/studio → **Download Android Studio** → 약관 동의 → 설치 (기본값 그대로, "Android Virtual Device" 체크 유지)
- [ ] 처음 실행 시 Setup Wizard: Install Type **Standard** → 라이선스 항목마다 **Accept** → Finish (SDK 수 GB 다운로드)
- 권장: 메모리 16GB 이상, 여유 저장 공간 20GB 이상

## 2. 저장소 받기
- [ ] 첫 화면 **Clone Repository** → **GitHub** → Log In via GitHub (비공개 저장소라 로그인 필요)
- [ ] `dohingee/bottlepost` 선택 → 저장 위치 지정 → Clone → 프로젝트 열기 묻는 창은 닫기
- [ ] **File → Open** → 받은 폴더 안의 **`android` 폴더** 열기 (최상위 `bottlepost` 폴더가 아님)
- [ ] 오른쪽 아래 Gradle sync가 끝날 때까지 대기 (처음 5~15분)

## 3. 설정 파일 넣기
- [ ] 받아 둔 `google-services.json`을 **`bottlepost/android/app/`** (`build.gradle.kts`가 있는 폴더)에 복사
- [ ] 위쪽 코끼리 아이콘 **Sync Project with Gradle Files**

## 4. 실행할 기기 (택 1)
**A. 실제 폰 (추천)**
- [ ] 설정 → 휴대전화 정보 → 소프트웨어 정보 → **빌드 번호 7번** 탭 → 개발자 모드
- [ ] 설정 → 개발자 옵션 → **USB 디버깅** 켜기 → USB 연결 → 폰에서 "허용"

**B. 에뮬레이터**
- [ ] Device Manager → + → Create Virtual Device → **Play Store ▶ 아이콘이 있는 기기**(예: Pixel 8) → 시스템 이미지 **API 36** 다운로드·선택 → Finish
- [ ] 에뮬레이터 설정 → 계정 → **Google 계정 추가** (Google Play 없는 이미지는 구글 로그인·푸시·번역 불가)

## 5. 서명 지문(SHA-1, SHA-256) 등록
- [ ] 아래쪽 **Terminal** 탭: Windows `gradlew signingReport` / Mac `./gradlew signingReport`
- [ ] `Variant: debug` 아래 **SHA1**, **SHA-256** 값 복사
- [ ] Firebase 콘솔 → ⚙️ 프로젝트 설정 → 내 앱 → `com.bottlepost.app` → **디지털 지문 추가** (두 값 모두)
- [ ] 같은 화면에서 `google-services.json` **다시 받아** 교체 → Gradle sync

## 6. App Check 디버그 토큰 등록 (필수)
서버는 진짜 앱에서 온 요청만 받습니다. 개발용 빌드는 디버그 토큰으로 허락해야 합니다.
- [ ] 기기 선택 후 **▶ Run 'app'**
- [ ] **Logcat** 검색창에 `DebugAppCheckProvider` → `Enter this debug secret into the allow list ...: <토큰>` 의 토큰 복사
- [ ] Firebase 콘솔 → 빌드 → **App Check** → 앱 탭
  - `com.bottlepost.app`이 미등록이면 **Play Integrity**로 등록 (SHA-256 입력)
  - **⋮ → 디버그 토큰 관리 → 디버그 토큰 추가** → 이름 `내 PC`, 값 붙여넣기 → 저장
- [ ] 앱 종료 후 다시 실행
- 기기마다, 앱 재설치 때마다 토큰이 새로 나오니 그때마다 추가

## 7. 편지 주고받기 시험 (기기 2대)
편지는 다른 사용자에게만 가므로, 기기 1대로는 "받을 수 있는 사람이 없어요"가 정상입니다.
- [ ] 기기 2대 준비(폰 + 에뮬레이터, 또는 에뮬레이터 2개), 두 번째 기기도 6단계 토큰 등록
- [ ] 기기 A: **남성**으로 가입 / 기기 B: **여성**으로 가입
- [ ] A에서 편지 쓰기 → 받는 사람 "여성" → 2자 이상 → 보내기
- [ ] B의 받은편지함에 도착 → 답장 → 두 기기 모두 **인연** 탭으로 이동
- [ ] 추가 확인: 번역 보기, 신고, 차단, 구글 계정 연결 → 백업 → 앱 삭제 후 "구글로 복원"

## 자주 나는 오류
| 증상 | 원인 → 해결 |
|---|---|
| Gradle sync "google-services.json is missing" | 파일 위치 확인 (`android/app/` 안) → 3단계 |
| 앱에서 계속 "문제가 생겼어요" | App Check 디버그 토큰 미등록 → 6단계 |
| 구글 로그인 즉시 실패 (Logcat에 `10:`) | SHA-1 미등록 또는 예전 `google-services.json` → 5단계 |
| "이 휴대폰에 구글 계정이 없어요" | 기기 설정에서 구글 계정 추가 |
| 알림이 안 옴 | 첫 실행 때 알림 권한 허용, 에뮬레이터는 Google Play 이미지인지 확인 |
| "받을 수 있는 사람이 없어요" | 정상. 다른 성별 계정의 두 번째 기기 필요 → 7단계 |
