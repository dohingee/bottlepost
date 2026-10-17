---
name: review-reports
description: 보틀포스트 신고 검토 기준과 제재 절차. 신고 큐 처리, 밴/정지/기각 판단, 이의 제기 재검토 시 사용.
---

# 신고 검토 절차

## 자동 처리 흐름 (참고)
`submitReport` → 서명 검증 → `onReportCreated` 봇: 규칙 엔진(스팸·사기) + OpenAI Moderation(무료) → `decide()` (`firebase/functions/src/moderation/decision.ts`).
봇은 확실한 건만 자동 처리하고 나머지를 `status=review`로 남긴다. 사람은 review 건만 본다.

## 판정 기준
| 상황 | 판정 | 명령 |
|---|---|---|
| 미성년자 성적 대상화·그루밍 확인 | 영구 정지 + 기관 신고 | `resolve <id> ban` → OPERATIONS.md §4 |
| 사기(금전·투자·코인 요구), 외부 유도 반복 | 30일 정지 (3회째 영구) | `resolve <id> suspend 30` |
| 원치 않는 성적 접근, 협박, 혐오 | 7일 정지 | `resolve <id> suspend 7` |
| 무례하지만 경미, 첫 위반 | 3일 정지 또는 기각 | `resolve <id> suspend 3` |
| 위반 아님 / 증거 미검증 / 보복성 신고 | 기각 (봇 정지 자동 해제) | `resolve <id> dismiss` |

- 스트라이크 3회 누적 시 자동 영구 정지(`THRESHOLDS.strikesForPermanent`).
- 영구 정지는 구글 연결 계정이면 이메일 해시로 재가입도 막힌다.

## 체크리스트
1. `npx tsx tools/moderation-cli.ts queue` (critical → high → medium → low 순)
2. `show <id>` — REPORTED + 검증된 편지만 증거. UNVERIFIED는 참고만.
3. `user <uid>` — 이전 신고·스트라이크 확인
4. 판정 → 운영자 승인 → `resolve`
5. 하루 1회 이상 큐를 비운다. critical은 24시간 안에 처리(아동 안전 표준 약속).

## 하지 말 것
- 신고 본문을 슬랙·이슈·문서로 복사
- CSAM 의심 내용을 다시 출력하거나 요약
- 신고자에게 처리 결과의 세부(상대 uid, 제재 종류)를 알려주기
