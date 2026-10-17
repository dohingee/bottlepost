---
name: cost-guard
description: 보틀포스트 비용 차단 장치(예산 알림 → 새 편지 일시정지 → 결제 분리) 설정, 점검, 복구 절차.
---

# 비용 차단 장치

## 동작
Cloud Billing 예산 → Pub/Sub `billing-budget` → `onBudgetAlert` (`firebase/functions/src/billing.ts`)
| 예산 대비 비용 | 동작 |
|---|---|
| 90% 미만 | 없음. 이전에 예산 때문에 멈췄다면 자동 재개 (새 달) |
| 90% 이상 | 새 첫 편지만 멈춤 (`config/runtime.paused`). 답장·수신·신고는 계속 |
| 100% 이상 | 프로젝트에서 결제 계정 분리 → Blaze 기능 중단 (`BILLING_HARD_CAP=true`일 때) |

예산 알림은 실제 비용보다 몇 시간 늦게 온다. 예산은 "절대 넘으면 안 되는 금액"보다 낮게 잡는다.

## 점검
- `npx tsx tools/cost-cli.ts status`
- 수동 일시정지/재개: `cost-cli.ts pause <사유>` / `cost-cli.ts resume`
- Firebase 콘솔 > 사용량 및 결제에서 실제 사용량 확인

## 결제가 분리된 뒤 복구
1. 원인 확인 (사용자 급증인지, 남용인지)
2. Google Cloud 콘솔 > 결제 > 내 프로젝트 > 보틀포스트 프로젝트에 결제 계정 다시 연결
3. 필요하면 예산 금액 상향
4. `npx tsx tools/cost-cli.ts resume`
5. 분리 중 실패한 기능은 없음(데이터는 유지). Functions는 결제 연결 즉시 다시 동작
