---
name: tune-matching
description: 편지 매칭 품질 진단과 매개변수 조정 절차. 매칭 실패, 성별 불균형, 미전달 편지, 답장률 개선 작업에 사용.
---

# 매칭 튜닝

## 현재 정책 (firebase/functions/src)
- `matching.ts` `isEligible`: 활성(7일 내), status=active, 원하는 성별, 수신자의 receiveFrom, (옵션) 같은 언어, 수신함 < 20, 차단·기존 대화 상대 제외
- `pickRecipient`: 랜덤 링(rand 필드)에서 30명 샘플 → 앞 5명 중 수신함이 가장 적은 사람
- `config.ts LIMITS`: 하루 첫 편지 익명 3 / 구글연결 10, 대화당 답장 30, 미전달 TTL 7일

## 진단
1. `npx tsx tools/matching-stats.ts`
2. 증상별 원인과 조치
| 증상 | 원인 후보 | 조치 |
|---|---|---|
| `no_recipient_available` 증가 | 한 성별 활성 풀 부족 | `activeWindowDays` 7→14, 마케팅 대상 조정 |
| 특정 사용자에게 편지 몰림 | 풀 작음 | `maxPendingInbox` 20→10 |
| 미전달(3일+) 누적 | 비활성 사용자 매칭 | `activeWindowDays` 축소, 푸시 토큰 상태 확인 |
| 답장률 < 10% | 스팸성 첫 편지, 언어 장벽 | 첫 편지 최소 길이 상향, 번역 버튼 노출 강화 |

## 변경 절차
1. `config.ts`/`matching.ts` 수정 → `test/matching.test.ts` 갱신
2. `cd firebase/functions && npm test && npm run test:emulator`
3. 운영자 승인 → `firebase deploy --only functions`
4. 1주 후 `matching-stats.ts`로 효과 비교
