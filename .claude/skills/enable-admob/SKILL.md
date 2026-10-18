---
name: enable-admob
description: AdMob 승인 후 "편지 보내기 전 전면광고"를 정책에 맞게 도입하는 절차.
---

# AdMob 도입

현재 상태: `ads/AdMobAdGate.kt` 연결 완료. 앱 ID `ca-app-pub-7472495816861016~6125650483`, 전면광고 단위 `ca-app-pub-7472495816861016/7063327631`(release만), 하단 배너 `ads/AdBanner.kt`(release 단위 ID는 `ADMOB_BANNER`, 비어 있으면 미표시), 디버그는 Google 테스트 ID.
출시 앱 광고는 Remote Config `ads_enabled`(기본 false)로 켭니다 — AdMob 앱 검토 승인 후 (`docs/RELEASE_GUIDE.md` 13단계).


## 1. 준비
- AdMob 앱 등록 → 앱 ID, 전면광고 단위 ID
- `https://<도메인>/app-ads.txt` 게시 (`firebase/hosting/app-ads.txt`)
- Remote Config 템플릿: `npx tsx tools/ad-config.ts --enable --interval 180 > firebase/remoteconfig.template.json`

## 2. 코드
- 의존성: `com.google.android.gms:play-services-ads`, `com.google.android.ump:user-messaging-platform`, `firebase-config`
- 매니페스트: `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" .../>` (AD_ID 권한은 ads SDK가 병합)
- `ads/AdMobAdGate.kt` 작성 후 `AppContainer.adGate` 교체
  - 앱 시작 시 UMP 동의 요청 → 동의 후에만 MobileAds 초기화
  - 전면광고 미리 로드, `beforeSend()`에서 Remote Config 조건(`ads_enabled`, 간격, 첫 N통 면제, 일일 상한) 충족 시에만 표시
  - 로드 실패·타임아웃(2초) 시 광고 없이 바로 통과 — 발송을 막지 않는다
- 디버그 빌드는 테스트 광고 단위만 사용

## 3. 정책 확인
- 전면광고는 사용자가 "보내기"를 누른 뒤 자연스러운 전환 시점에만
- 앱 시작·종료, 편지 읽기, 신고·차단·삭제 흐름 중 광고 금지
- 18+ 앱이지만 성인용 광고 카테고리는 차단 설정(AdMob 차단 관리)

## 4. 문서·콘솔
- 개인정보처리방침 AdMob 항목 활성화, 데이터 보안: 기기 ID(광고 ID) 수집·공유 "광고" 목적
- Play Console 앱 콘텐츠 > 광고 포함 "예", 광고 ID 선언
- `tools/policy-check.sh`의 AD_ID 경고 확인 후 체크리스트 갱신
