# 보틀포스트 — 기획 및 구현 계획

## Context
빈 작업 디렉터리(/home/user)에서 새 안드로이드 앱을 만든다. 컨셉: 사용자가 **받을 상대의 성별만 정해 미지의 사용자에게 "편지(메일)"를 보내고**, 답장이 오면 인연이 이어진다. 로그인 없이 시작하고, 구글 계정을 연결하면 재설치/기기변경 시 대화 복원. 대화 내용은 **서버에 보관하지 않고**, 신고된 내용만 서버에서 분석·처리(밴). 다국어 번역, 이용약관, 운영 도구(기능별 에이전트·스킬·도구) 포함. 광고는 나중에(구조만 준비).

확정된 결정: **Kotlin + Jetpack Compose / Firebase / 만 18세 이상 / 신고 분석은 무료 우선**.

---

## 1. 아키텍처 핵심 (요구사항 9 "서버 미보관"과 양립시키는 방법)
- **저장-후-전달(store-and-forward) 중계**: 편지는 Firestore `deliveries/{id}`에 *전달될 때까지만* 존재. 수신 기기가 받아 로컬에 저장하고 `ack` 호출 → 즉시 삭제. 미수신 편지는 Firestore **TTL 정책으로 7일 후 자동 삭제**.
- 서버가 보관하는 것은 **메타데이터만**: 스레드(참여자 UID 쌍, 생성일), 사용자 프로필(성별·출생연도·언어), 차단 목록, 발송량 카운터. 본문 없음.
- **대화 내역은 기기 로컬(Room DB)** 에만 저장.
- **신고 시에만** 신고자 기기가 해당 편지(+같은 스레드 직전 몇 통)를 동의 화면을 거쳐 서버 `reports/`로 업로드 → 분석 → 처리 후 90일 보관 뒤 삭제(약관·개인정보처리방침에 명시).

## 2. 계정 / 수집 정보 (요구사항 3, 4)
- 첫 실행: **Firebase 익명 인증** → 온보딩에서 수집.
- **필수(추천)**: 성별(남/여), 출생연도(만 18세 확인), 앱 언어/국가(번역·매칭 언어), 받을 상대 성별(남/여/상관없음), 약관·개인정보 동의.
- **선택**: 닉네임(편지 서명용), 관심사 태그 3개 이내.
- **수집하지 않음**: 실명, 전화번호, 정확한 위치, 사진, 기기 하드웨어 ID(Play 정책 위반 소지).
- **구글 계정 연결**: Credential Manager(Sign in with Google) → `linkWithCredential`로 익명 UID를 그대로 유지. 재설치 시 구글 로그인하면 같은 UID 복구.
- **대화 복원**: 연결 사용자는 **사용자 본인 Google Drive의 appDataFolder**(앱 전용 숨김 영역)에 로컬 DB를 암호화 백업(자동: 하루 1회 WorkManager + 수동 버튼). 서버엔 여전히 본문 없음 → 요구사항 9와 충돌 없음.
- **계정 삭제**: 앱 내 + 웹 페이지 둘 다 제공(Play 필수).

## 3. 핵심 기능 흐름
- **편지 보내기** `sendLetter`(Callable Function): App Check 검증 → 밴/발송한도 확인(익명 3통/일, 구글연결 10통/일 등) → 사전 필터(URL·전화번호·메신저 ID 차단) → 대상 성별의 최근 7일 활성·미차단·수신함 여유 있는 사용자 중 **랜덤 1명** 선정(`rand` 필드 인덱스 쿼리) → 스레드 생성 + delivery 기록 + FCM 푸시.
- **답장** `replyLetter(threadId)`: 스레드 참여자만 가능. 답장이 오가면 "인연" 스레드로 표시.
- **수신** `ackDelivery`: 받은 즉시 삭제.
- **차단** / **신고** `submitReport` / **계정삭제** `deleteAccount`.
- 광고 자리: `AdGate` 인터페이스 + `NoOpAdGate`(현재) — 추후 AdMob 전면광고를 "보내기" 직전에 연결(빈도 제한, UMP 동의 SDK 포함).

## 4. 신고 처리 봇 — 무료 + 높은 정확도 (요구사항 5)
비용 0에 가깝게 3단 파이프라인(Cloud Function `onReportCreated`):
1. **규칙 엔진(무료, 자체)**: URL/전화/메신저 ID/코인지갑/금전 요구 패턴 → 스팸·사기 판정. 신고자 수(고유), 발송량 대비 신고율, 계정 나이 점수화.
2. **무료 AI 분류기: OpenAI Moderation API(`omni-moderation-latest`, 무료·다국어)** → 성적/미성년 성적/괴롭힘/혐오/폭력/자해 점수. (스팸·사기는 1단계가 담당)
3. **판정**: 점수 합산 → `자동 밴` / `경고` / `관리자 검토 큐` / `기각`. **미성년 관련은 항상 즉시 정지 + 관리자 검토**(법적 신고 의무 대비).
- 대안 메모: 정확도를 더 올리고 싶으면 Claude Haiku 5.5(입력 $0.10/1M 토큰) 사용 시 신고 1만 건 ≈ 월 1~2달러 수준 — 설정 플래그로 교체 가능하게 `Classifier` 인터페이스로 분리.
- 밴: UID 정지 + 연결된 구글 계정 이메일 해시 블랙리스트 + Play Integrity 판정으로 재가입 회피 억제.

## 5. 번역 (요구사항 6)
- **ML Kit 온디바이스 번역 + 언어 감지**(무료, 오프라인, 본문이 서버로 안 나감 → 요구사항 9와 일치). 편지 상세에서 "번역 보기" 토글, 언어팩 첫 사용 시 Wi‑Fi 다운로드.
- UI 문자열 다국어: ko, en, ja, es, pt-BR, id, vi, th (strings.xml) + 앱별 언어 설정(per-app language).

## 6. 디자인 추천 (요구사항 2)
- 메타포: **"바다에 띄운 유리병 편지 / 우체통"**. Material 3 + 다이내믹 컬러, 기본 팔레트는 따뜻한 종이색 + 잉크 블루, 다크모드 지원, edge-to-edge.
- 화면: 온보딩(성별·연령·언어·약관) → 하단 탭 **[받은편지 | 보낸편지 | 인연(답장 오간 스레드) | 설정]** + 가운데 FAB "편지 쓰기".
- 편지쓰기: 받을 성별 칩 선택, 편지지 스타일 본문(최대 1,000자), 보내기 → (추후 광고) → 종이비행기 애니메이션.
- 편지 상세: 스레드형(메일 대화 보기), 번역 토글, 답장/차단/신고 메뉴.
- 설정: 구글 계정 연결·백업/복원, 언어, 알림, 차단 목록, 약관/개인정보, 계정 삭제.

## 7. Google 정책 준수 체크 (요구사항 1)
- **targetSdk 36 / compileSdk 36, minSdk 26** (2026-08-31부터 신규 앱·업데이트 API 36 타깃 필요 — 출시 전 Play Console 재확인).
- 16KB 페이지 크기 호환, edge-to-edge·predictive back 대응, 권한 최소화(INTERNET, POST_NOTIFICATIONS 런타임 요청).
- **UGC 정책**: 약관 동의, 신고·차단 기능, 적극적 모더레이션.
- **아동 안전 표준(CSAE) 정책**: 소셜/데이팅 앱 → 공개된 아동안전 표준 웹페이지, 인앱 신고, CSAM 대응 절차, 아동안전 담당 연락처를 Play Console에 등록.
- 대상 연령 18+ 설정(가족 정책 제외), IARC 콘텐츠 등급, **데이터 보안(Data safety) 양식**, 개인정보처리방침 URL, **계정 삭제 웹 URL**.
- 개인 개발자 계정이면 **비공개 테스트 12명 × 14일** 후 프로덕션 신청 가능 — 일정에 반영.
- Play Integrity API + Firebase App Check.

## 8. 앱 이름 (요구사항 7) — **확정: 보틀포스트 (Bottlepost)**
- 웹 검색 기준 같은 이름의 앱·상표는 발견되지 않음. 유사 컨셉 경쟁 앱: Bottled(Honi Inc.), BottleTalk, Drift Bottle, BottleApp.
- 출시 전 직접 확인: Play 스토어 검색, KIPRIS(한국)·USPTO·EUIPO 상표(9류/45류), 도메인. 독일 "Flaschenpost"(=bottle post, 음료배달 브랜드)는 업종이 달라 위험 낮음.
- 스토어 표기 예: "Bottlepost – 낯선 이에게 띄우는 편지". 패키지명 예: `com.<개발자도메인>.bottlepost`.

## 9. 운영 준비물 + 기능별 에이전트·스킬·도구 (요구사항 11)
준비물: Play 개발자 계정($25), 도메인 + Firebase Hosting(약관·개인정보·계정삭제·아동안전 페이지), 지원 이메일, Firebase **Blaze 요금제**(Functions 필요, 무료 한도 내 운영 가능) + 예산 알림, Crashlytics, 관리자 웹(Firebase Hosting + 관리자 custom claim), 광고 수익 발생 시 사업자등록.

기능별 1 에이전트 / 1 스킬 / 1 도구 (Claude Code 운영 자산, 저장소에 포함):

| 기능 | 에이전트 `.claude/agents/` | 스킬 `.claude/skills/` | 도구 `tools/` |
|---|---|---|---|
| 신고·모더레이션 | `moderation-reviewer` (검토 큐 판정 보조) | `review-reports` (판정 기준·밴 절차) | `moderation-cli.ts` (큐 조회/밴/해제) |
| 편지 매칭·중계 | `matching-monitor` (매칭 실패·불균형 분석) | `tune-matching` (한도·풀 조정 절차) | `matching-stats.ts` (성별 풀/전달률 통계) |
| 계정·백업 | `account-support` (복원·삭제 문의 처리) | `handle-account-request` | `account-cli.ts` (조회/삭제/연결상태) |
| 번역·현지화 | `localization-agent` (strings 번역·검수) | `add-locale` (새 언어 추가 절차) | `check-strings.ts` (누락 키 검사) |
| 정책·릴리즈 | `play-policy-auditor` (정책 체크리스트 점검) | `release-checklist` (빌드→테스트→출시) | `policy-check.sh` (targetSdk·권한·매니페스트 검사) |
| 고객지원(CS) | `support-responder` (다국어 답변 초안) | `support-macros` (FAQ·답변 템플릿) | `support-inbox.ts` (문의/이의제기 목록) |
| 광고(추후) | `ads-compliance` | `enable-admob` | `ad-config.ts` (빈도 제한 Remote Config) |

## 10. 저장소 구조 (생성할 파일)
```
/home/user/
  docs/PLAN.md, docs/POLICY_CHECKLIST.md, docs/OPERATIONS.md, docs/APP_NAMES.md
  docs/legal/terms_ko.md, terms_en.md, privacy_ko.md, privacy_en.md, child_safety.md
  android/  (Gradle KTS + version catalog, app 모듈)
    app/src/main/java/.../ ui/(onboarding, inbox, compose, thread, settings, theme)
                         data/local(Room), data/remote(Firestore/Functions), auth/, backup/(Drive appData),
                         translate/(ML Kit), report/, ads/(AdGate)
    app/src/main/res/values-*/strings.xml (8개 언어)
  firebase/ firebase.json, firestore.rules, firestore.indexes.json
            functions/src/ (sendLetter, replyLetter, ackDelivery, submitReport, onReportCreated,
                            moderation/{rules.ts, openaiModeration.ts, classifier.ts}, deleteAccount, admin)
            hosting/ (법적 페이지, 계정삭제 페이지, 관리자 페이지)
  .claude/agents/*.md, .claude/skills/*/SKILL.md, tools/*
```

## 11. 이용약관 초안 목차 (요구사항 10)
목적·정의 / 만 18세 이상 이용 / 익명 이용과 구글 계정 연결 / 편지 서비스(서버 미보관·전달 후 삭제·7일 미수신 자동삭제) / 금지행위(스팸·사기·성적 콘텐츠·괴롭힘·미성년자 관련·외부 연락처 유도·상업 홍보) / 신고 및 제재(신고 내용만 수집·분석, 단계별 제재, 이의제기) / 번역 기능 면책 / 광고 / 계정 삭제 / 책임 제한 / 준거법(대한민국)·분쟁 / 문의처. 개인정보처리방침·아동안전 표준도 함께 작성.

## 12. 진행 순서
1. 문서: PLAN, 정책 체크리스트, 운영 가이드, 앱 이름, 약관·개인정보·아동안전 초안
2. Firebase 백엔드: 규칙, 인덱스, Functions(중계·신고 파이프라인), 호스팅 페이지
3. Android 앱: 온보딩 → 메일함/쓰기/상세 → 번역 → 신고·차단 → 구글 연결·Drive 백업 → 설정·계정삭제
4. 운영 자산: 에이전트·스킬·도구
5. 검증
6. 코드 보관: 이 작업 공간은 임시 환경이므로 git 저장소로 초기화해 커밋하고, 사용자의 GitHub 저장소에 push (저장소가 없으면 생성 방법 안내). 실서비스는 Firebase(서버)와 사용자 휴대폰(앱)에서 동작하며 이 컴퓨터는 개발 때만 필요.

## 13. 검증
- Functions: `npm run build` + Jest 단위 테스트(규칙 엔진, 매칭 선정, 판정 로직) + Firebase Emulator로 `firestore.rules` 테스트(타인 delivery 접근 불가, 본문 필드 서버 잔존 없음).
- Android: Android SDK cmdline-tools를 스크래치패드에 설치 시도 → `./gradlew assembleDebug testDebugUnitTest lint`. 네트워크 제한으로 SDK 설치 불가 시 빌드 미검증임을 명시 보고.
- `tools/policy-check.sh`로 targetSdk 36·권한·매니페스트 자동 점검.
- 실제 Firebase 프로젝트 연결(google-services.json), Play Console 등록, OpenAI API 키 발급은 사용자가 직접 해야 하는 단계로 안내.

---

## 14. 구현 메모 (계획 대비 변경·추가)
- **편지 서명(HMAC)** 추가: 서버가 편지를 보관하지 않아도, 신고된 편지가 조작되지 않았음을 검증할 수 있음 (`functions/src/signing.ts`). 조작된 편지는 증거에서 제외.
- **관리자 페이지** 대신 운영 CLI(`tools/moderation-cli.ts` 등) + 관리자 callable(`adminReviewQueue`, `adminResolveReport`)로 시작. 웹 관리자 화면은 필요해지면 callable 위에 추가.
- **신고 = 자동 차단**: 신고하면 상대가 차단되고 대화가 닫힘.
- **이의 제기**: 정지된 사용자가 앱에서 이의를 제기 → `appeals/` → `tools/support-inbox.ts`.
- **같은 편지 반복 발송 차단**(스팸 방지), 수신자도 "첫 편지를 받고 싶은 상대" 성별을 고를 수 있음.
- 백업 암호화 키는 서버(`users/{uid}/private/backup`)에, 백업 파일은 사용자 드라이브에 — 어느 한쪽만으로는 대화를 읽을 수 없음.
- 출생연도만 받으므로 나이는 보수적으로 계산(올해 생일이 안 지났다고 가정).
