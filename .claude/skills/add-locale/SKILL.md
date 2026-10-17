---
name: add-locale
description: 앱에 새 언어를 추가하거나 문구를 추가·수정할 때의 현지화 절차.
---

# 새 언어 추가

1. 폴더 생성: `android/app/src/main/res/values-<code>/strings.xml` (인도네시아어는 `values-in`, 지역 구분이 필요하면 `values-pt-rBR`)
2. `values/strings.xml`의 **translatable 문구 전부** 번역. `translatable="false"` 항목(앱 이름, 언어 이름)은 복사하지 않는다. 새 언어의 자기 이름은 `values/strings.xml`에 `lang_<code>`로 추가.
3. 코드 반영
   - `ui/common/Common.kt`: `SUPPORTED_LANGS`, `langLabel()`
   - `firebase/functions/src/config.ts`: `SUPPORTED_LANGS`
4. 번역 가능 여부: ML Kit 번역 지원 언어인지 확인 (https://developers.google.com/ml-kit/language/translation/translation-language-support). 미지원이면 UI만 현지화되고 편지 번역은 "아직 번역할 수 없는 언어" 안내가 나온다.
5. 법률 문서: 해당 국가 출시 시 `docs/legal/`에 번역본 추가 → `node firebase/scripts/build-legal.mjs`
6. 검사: `npx tsx tools/check-strings.ts` (0 problem) → `bash tools/policy-check.sh`
7. Play Console 스토어 등록정보에 해당 언어 설명·스크린샷 추가

# 문구 추가·수정
- 새 키는 `values/`(영어)에 먼저 추가 → 모든 locale에 번역 추가 → `check-strings.ts`
- 서버 오류 코드를 새로 만들면 `ui/common/Errors.kt`에 매핑하고 문구를 추가한다.
