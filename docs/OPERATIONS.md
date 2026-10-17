# 보틀포스트 운영 가이드

## 1. 운영에 필요한 것

| 항목 | 내용 | 비용 |
|---|---|---|
| Google Play 개발자 계정 | 신원 확인 필요. 개인 계정은 첫 출시 전 비공개 테스트 12명 × 14일 | $25 (1회) |
| Firebase 프로젝트 (Blaze 요금제) | Functions·예약 작업에 필요. 초기 트래픽은 무료 한도 안에서 운영 가능. **예산 알림을 꼭 설정** | 사용량 기반 |
| 도메인 | 약관·개인정보·계정삭제·아동안전 페이지용. 처음엔 `*.web.app` 무료 도메인도 가능 | 연 1~2만 원 |
| 이메일 3개 | support@ (문의), privacy@ (개인정보), safety@ (아동안전 담당, Play Console 등록) | 무료~ |
| OpenAI API 키 | 신고 분석용 Moderation API (무료). 없으면 `disabled`로 두고 규칙 + 사람 검토만 사용 | 무료 |
| 업로드 키 (keystore) | 안전한 곳 2군데 이상 백업. Play 앱 서명 사용 | 무료 |
| 사업자등록 | 광고 수익이 생기기 시작하면 | — |
| 운영자 | 하루 1회 신고 큐 확인(critical은 24시간 안), 주 1회 지표 확인 | — |

## 2. 기능별 에이전트 · 스킬 · 도구

Claude Code에서 이 저장소를 열면 아래 에이전트와 스킬이 자동으로 인식됩니다. 도구는 `tools/`에서 직접 실행할 수도 있습니다.

| 기능 | 에이전트 (`.claude/agents/`) | 스킬 (`.claude/skills/`) | 도구 (`tools/`) |
|---|---|---|---|
| 신고·모더레이션 | `moderation-reviewer` | `review-reports` | `moderation-cli.ts` |
| 편지 매칭·중계 | `matching-monitor` | `tune-matching` | `matching-stats.ts` |
| 계정·백업 | `account-support` | `handle-account-request` | `account-cli.ts` |
| 번역·현지화 | `localization-agent` | `add-locale` | `check-strings.ts` |
| 정책·릴리즈 | `play-policy-auditor` | `release-checklist` | `policy-check.sh` |
| 고객지원 | `support-responder` | `support-macros` | `support-inbox.ts` |
| 광고 (승인 후) | `ads-compliance` | `enable-admob` | `ad-config.ts` |

도구 실행 준비:
```bash
cd tools && npm install
gcloud auth application-default login
export GOOGLE_CLOUD_PROJECT=<firebase 프로젝트 ID>
npx tsx moderation-cli.ts queue
```

## 3. 신고 처리 봇 (무료 구성)

```
신고 (앱) ──► submitReport ──► 서명 검증 ──► reports/{id}
                                              │
                         onReportCreated ◄────┘
                           1. 규칙 엔진 (스팸·사기·외부유도)      무료, 자체 코드
                           2. OpenAI Moderation (성적·미성년·괴롭힘·혐오·폭력·자해)  무료
                           3. decide(): 자동정지 / 영구정지 / 사람 검토 / 기각
```

- **편지 서명**: 서버는 편지를 보관하지 않지만, 보낼 때 HMAC 서명을 붙입니다. 신고 시 신고자 기기의 편지를 서명으로 검증하므로 조작된 신고를 걸러냅니다.
- **보수적 자동화**: 자동 조치는 기간 정지만. 영구 정지는 스트라이크 3회 또는 사람 판단(미성년자 성착취 고확신은 예외).
- **미성년자 관련**: 항상 즉시 정지 + 사람 검토.
- 정확도를 더 올리고 싶으면 `moderation/classifier.ts`에 다른 분류기를 추가할 수 있습니다(예: 저가 LLM — 신고 1만 건에 월 1~2달러 수준).

## 4. 아동 성착취물(CSAM) 대응 절차

1. 해당 신고는 봇이 자동 정지 처리함. 큐에서 `critical`로 표시됨.
2. 운영자가 내용 확인(최소한으로). **복사·재전송·스크린샷 금지.**
3. 확인되면 `moderation-cli.ts resolve <id> ban`.
4. 신고: 한국 — 경찰청 사이버수사국(ecrm.police.go.kr), 방송통신심의위원회 / 미국 이용자 관련 — NCMEC CyberTipline. 국가별 의무는 출시 국가마다 확인.
5. 증거 보존: 해당 report 문서의 `expireAt`을 지워 TTL 삭제를 막고, 수사기관 요청 시 제공. 사건 종료 후 삭제.
6. 처리 기록(일시, 신고 번호)을 운영 기록에 남김.

## 5. 일상 운영 루틴

| 주기 | 할 일 | 도구 |
|---|---|---|
| 매일 | 신고 검토 큐 비우기 | `moderation-cli.ts queue` |
| 매일 | 이의 제기 확인 | `support-inbox.ts list` |
| 주 1회 | 매칭 지표 (성별 풀, 답장률, 미전달) | `matching-stats.ts` |
| 주 1회 | Crashlytics, Firebase 사용량·비용 | 콘솔 |
| 출시마다 | 정책 검사 + 테스트 | `policy-check.sh`, `release-checklist` 스킬 |
| 매년 8월 | targetSdk 상향 | `play-policy-auditor` |

## 6. 데이터 보관 (자동 삭제)
Firestore TTL 정책으로 자동 삭제됩니다. 배포 후 콘솔에서 TTL이 "제공됨" 상태인지 확인하세요.

| 데이터 | 기간 | 필드 |
|---|---|---|
| 전달 대기 편지 | 받는 즉시 삭제, 최대 7일 | `deliveries.expireAt` |
| 신고 | 처리 후 90일 | `reports.expireAt` |
| 영구 정지 이메일 해시 | 3년 | `bans.expireAt` |
| 이의 제기 | 1년 | `appeals.expireAt` |
