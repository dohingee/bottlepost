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
- [ ] 광고 ID 미사용 선언 (광고 도입 전). 광고 도입 시 `AD_ID` 권한·선언 업데이트

| 데이터 유형 (Play 분류) | 수집 | 공유 | 필수/선택 | 목적 |
|---|---|---|---|---|
| 개인 정보 > 기타 정보 (성별, 출생연도) | 예 | 아니요 | 필수 | 앱 기능, 계정 관리 |
| 개인 정보 > 이메일 주소 (Google 연결 시) | 예 | 아니요 | 선택 | 계정 관리 |
| 메시지 > 기타 인앱 메시지 | 예 (전송 중 일시 저장, 신고 시 보관) | 예 (신고 건만 OpenAI) | 필수 | 앱 기능, 사기 방지·보안 |
| 앱 활동 > 기타 작업 (발송 횟수 등) | 예 | 아니요 | 필수 | 사기 방지·보안 |
| 앱 정보 및 성능 > 비정상 종료 로그 | 예 | 아니요 | 필수 | 분석 |
| 기기 또는 기타 ID (Firebase 설치 ID, 푸시 토큰) | 예 | 아니요 | 필수 | 앱 기능 |

- 전송 중 암호화: 예 / 삭제 요청 가능: 예

## F. 계정·인증
- [ ] Google 로그인은 Credential Manager(`androidx.credentials`) 사용 (구 GoogleSignIn API는 지원 중단)
- [ ] Drive 스코프는 `drive.appdata`만 요청 (사용자 파일 접근 없음)
- [ ] 로그인 없이 핵심 기능 사용 가능 (요구사항 3) — 리뷰어용 테스트 계정 불필요하지만 "앱 액세스" 항목에 설명 기재

## G. 광고 (도입 시)
- [ ] AdMob 계정 승인 후 `NoOpAdGate` → `AdMobAdGate` 교체
- [ ] 전면광고는 "보내기" 버튼을 누른 뒤 자연스러운 전환 시점에만, 빈도 제한(예: 3분에 1회)
- [ ] 앱 실행 직후·종료 시 전면광고 금지, 실수 클릭 유도 배치 금지
- [ ] EEA/UK/스위스 사용자 대상 UMP(사용자 메시지 플랫폼) 동의 SDK
- [ ] `app-ads.txt` 도메인 게시

## H. 출시 절차
- [ ] 개인 개발자 계정: **비공개 테스트 12명 이상 × 연속 14일** 후 프로덕션 신청
- [ ] 개발자 신원 확인(신분증·연락처) 완료
- [ ] 스토어 등록정보: 스크린샷에 실제 사용자 개인정보 노출 금지, "만남 보장" 등 과장 문구 금지
