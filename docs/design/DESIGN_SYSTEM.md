# CS한입(ByteCS) 디자인 시스템 (Design System)

> 이 문서는 개발자가 **이 문서만 보고** Compose Multiplatform(Android + Web/Wasm) 클라이언트를 구현할 수 있도록 작성된 계약(contract)이다.
> 근거: `docs/도메인 명세서.md`, `docs/ux/UX 핵심 가이드.md`, `docs/ux/UX 에러 응답 가이드.md`, `docs/design/00~07`(공통 원칙·페이지 요구사항).
>
> **값의 성격(필독):** 이 서비스는 아직 확정된 레퍼런스 시안이 없다. 따라서 아래 색·치수 토큰은 레퍼런스에서 '추출'한 값이 아니라, `00 공통 디자인 원칙`의 테마와 UX 가이드에서 도출한 **제안 v0**이다. 시안·사용성 검증으로 확정하며, 확정 전까지도 **화면 코드는 raw hex·raw dp를 쓰지 않고 이 토큰만 참조**한다(값이 바뀌어도 토큰 이름은 유지).
>
> **재활용 원칙:** 이 시스템은 Resumaker 디자인 시스템의 **방법·구조·컴포넌트 규율만** 차용했다. 도메인(문제/힌트/세션/복습)과 **비처벌 색 체계·다크 모드**는 CS한입 고유다. 도메인에 없는 개념(경험유형·산출물·이력서 양식 등)은 포함하지 않는다.

---

## 0. 토큰을 담는 곳 (구현 진입점)

모든 디자인 토큰은 코드에서 단일 출처로 관리한다. 화면 코드에서 **raw hex·raw dp를 절대 쓰지 않는다**(검토 게이트: "임의 색/간격/라운드 0건").

```
app/shared/src/commonMain/kotlin/watson/bytecs/ui/theme/
 ├─ Color.kt      // BcsColors (라이트/다크) — §2
 ├─ Type.kt       // BcsType (Pretendard + 코드용 monospace) — §3
 ├─ Dimens.kt     // BcsSpacing / BcsRadius / BcsElevation / BcsMotion — §4
 ├─ HintStyle.kt  // 힌트 종류·레벨, 문제 상태 시각 매핑 — §6
 └─ Theme.kt      // BcsTheme { MaterialTheme(colorScheme=light/dark, typography=...) } + CompositionLocal
```

권장 패턴: Material3 `MaterialTheme`를 베이스로 깔되, 브랜드 고유 토큰(힌트 위계색·비처벌 상태색·surface 단계 등 Material 스킴에 1:1로 안 맞는 것)은 `CompositionLocal`(`LocalBcsColors`, `LocalBcsSpacing`)로 추가 노출한다. `BcsTheme`는 시스템 다크 설정(또는 계정·설정 화면 토글, §화면 06)을 받아 라이트/다크 스킴을 전환한다.

---

## 1. 디자인 원칙 (Design Principles)

`00 공통 디자인 원칙`의 테마를 시각 언어로 옮긴 것이다. 모든 컴포넌트 결정은 이 원칙을 우선한다.

1. **부담 없는 미니멀 (Bite-sized Minimal).** '시험지'가 아니라 '한입 간식'. 화면당 요소를 최소화하고, 뉴트럴 표면 위에 절제된 액센트 하나만 강하게 둔다. 색을 아껴 정보가 스스로 위계를 갖게 한다. — 존재 이유(공부 부담 제거).
2. **부드러운 라운드 (Soft Rounded).** 카드·입력·버튼은 16dp, 칩은 12dp, 태그·진행 점은 full. 날카로운 모서리를 피해 위협적이지 않게.
3. **넉넉한 여백·집중 (Generous Whitespace & Focus).** 콘텐츠 가로 패딩 20dp, 문제 영역은 한 번에 하나에 시선 집중. 읽기·입력 가독 폭 확보.
4. **한 화면 한 행동 (One Primary Action).** 화면마다 강조 Primary는 하나(문제 풀이 화면에선 '정답 제출'). 힌트·정답 보기·신고는 secondary로 위계를 낮춘다. 주요 CTA는 **모바일 엄지 영역(하단)** 고정.
5. **무낙인·정직한 상태 (Non-punitive & Honest State).** ⭐️ **오답·불일치·실패에 경고색(빨강)을 쓰지 않는다.** 틀림은 '아직'일 뿐이며 중립 톤으로 격려한다. 빨강(danger)은 오직 파괴적 행동(계정 삭제)에만. Empty/Loading/Error는 1급 컴포넌트로 취급하고 막다른 길을 만들지 않는다.
6. **라이트·다크 양립 (Light & Dark).** 개발자 선호와 접근성을 위해 두 테마를 1급으로 제공한다. 모든 색 토큰은 라이트/다크 쌍을 가진다.

---

## 2. 색상 토큰 (Color Tokens)

> 제안 v0 → **primary는 시안 검증으로 확정**(7개 시안 전부 blue 사용). 뉴트럴은 Tailwind slate 계열, 브랜드 액센트는 blue, 정답은 emerald를 기준으로 한다. 라이트/다크 쌍으로 정의한다.

### 2.1 브랜드·뉴트럴 (라이트 / 다크)

| 토큰 | 역할 | 라이트 Hex | 다크 Hex |
|------|------|-----------|----------|
| **primary** | 브랜드·주요 액션 | `#2563EB` (blue-600) | `#60A5FA` (blue-400) |
| primaryPressed | Primary 눌림 | `#1D4ED8` (blue-700) | `#3B82F6` (blue-500) |
| onPrimary | Primary 위 텍스트 | `#FFFFFF` | `#172554` (blue-950) |
| primaryContainer | 옅은 강조 배경(힌트·정보) | `#EFF6FF` (blue-50) | `#1E3A8A` (blue-900) |
| onPrimaryContainer | 위 배경 위 텍스트 | `#1D4ED8` (blue-700) | `#DBEAFE` (blue-100) |
| primaryBorder | primaryContainer 테두리 | `#DBEAFE` (blue-100) | `#1D4ED8` (blue-700) |
| **background** | 앱 캔버스 배경 | `#F8FAFC` (slate-50) | `#0B1120` (slate-950 근사) |
| **surface** | 카드·표면 | `#FFFFFF` | `#0F172A` (slate-900) |
| surfaceSubtle | 입력칸·옅은 표면 | `#F8FAFC` (slate-50) | `#1E293B` (slate-800) |
| **border** | 기본 테두리(입력) | `#E2E8F0` (slate-200) | `#334155` (slate-700) |
| borderSubtle | 카드 테두리·디바이더 | `#F1F5F9` (slate-100) | `#1E293B` (slate-800) |
| **textPrimary** | 제목·주텍스트 | `#0F172A` (slate-900) | `#F8FAFC` (slate-50) |
| **textSecondary** | 보조 설명 | `#64748B` (slate-500) | `#94A3B8` (slate-400) |
| **textTertiary** | 비활성·플레이스홀더 | `#94A3B8` (slate-400) | `#64748B` (slate-500) |
| textLabel | 폼 라벨 | `#334155` (slate-700) | `#CBD5E1` (slate-300) |
| textBody | 본문 설명 | `#475569` (slate-600) | `#CBD5E1` (slate-300) |

### 2.2 상태색 (Status) — ⭐️ 비처벌 원칙

| 토큰 | 역할 | 전경(라이트/다크) | 옅은 배경(라이트/다크) | 사용 규칙 |
|------|------|------|------|------|
| **success** | **정답**·완료 | `#059669` / `#34D399` | `#ECFDF5` / `#064E3B` | 정답 맞힘·세션 완료·**폼 인라인 검증 통과** 등 긍정 순간에만. 오답 피드백에 쓰지 않는다. |
| **info** | 힌트·오답 교정·안내 | primary와 동일 | primaryContainer와 동일 | 배경지식 힌트·오답 교정 힌트·정보 카드. **경고 아님.** |
| **neutralNudge** | **불일치·재시도** | `#64748B` / `#94A3B8` (=textSecondary) | `#F8FAFC` / `#1E293B` (=surfaceSubtle) | ⭐️ 오답/불일치 피드백. **절대 빨강 금지.** 중립·격려 톤. |
| **danger** | **파괴적 행동만** | `#DC2626` / `#F87171` | `#FEF2F2` / `#450A0A` | ⚠️ **계정 삭제 등 되돌릴 수 없는 행동에만.** 오답 피드백에 쓰지 않는다. |
| **streak** | 연속 학습(스트릭) 표시 | `#F97316` (orange-500) / `#FB923C` (orange-400) | `#FFF7ED` (orange-50) / `#431407` (orange-950) | ⚠️ **스트릭 표시에만.** 스트릭 상실 공포 연출(불이 꺼진다 등)에 쓰지 않는다. |
| difficulty | 난이도 표시(은은) | `#94A3B8` / `#64748B` (=textTertiary) | — | 난이도 점/라벨. 강조·압박 금지. |

> **가장 중요한 규칙:** 사용자가 틀렸을 때 화면에 빨강·경고 아이콘·"오답!" 같은 처벌 신호가 나오면 **원칙 5 위반**이다. 불일치는 `neutralNudge`로, 정답은 `success`로만 표현한다.

### 2.3 Material3 ColorScheme 매핑

```kotlin
// 라이트
val BcsLightColorScheme = lightColorScheme(
    primary            = Color(0xFF2563EB),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFEFF6FF),
    onPrimaryContainer = Color(0xFF1D4ED8),
    secondary          = Color(0xFF64748B), // 보조/저강조 액션
    onSecondary        = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9), // slate-100 (Secondary 버튼 배경)
    onSecondaryContainer = Color(0xFF334155),
    background          = Color(0xFFF8FAFC),
    onBackground        = Color(0xFF0F172A),
    surface             = Color(0xFFFFFFFF),
    onSurface           = Color(0xFF0F172A),
    surfaceVariant      = Color(0xFFF8FAFC), // 입력·옅은 표면
    onSurfaceVariant    = Color(0xFF64748B),
    outline             = Color(0xFFE2E8F0), // 입력 테두리
    outlineVariant      = Color(0xFFF1F5F9), // 카드 테두리·디바이더
    error               = Color(0xFFDC2626), // ⚠️ 파괴적 행동 전용
    onError             = Color(0xFFFFFFFF),
    errorContainer      = Color(0xFFFEF2F2),
    onErrorContainer    = Color(0xFFB91C1C),
)

// 다크
val BcsDarkColorScheme = darkColorScheme(
    primary            = Color(0xFF60A5FA),
    onPrimary          = Color(0xFF172554),
    primaryContainer   = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary          = Color(0xFF94A3B8),
    onSecondary        = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFCBD5E1),
    background          = Color(0xFF0B1120),
    onBackground        = Color(0xFFF8FAFC),
    surface             = Color(0xFF0F172A),
    onSurface           = Color(0xFFF8FAFC),
    surfaceVariant      = Color(0xFF1E293B),
    onSurfaceVariant    = Color(0xFF94A3B8),
    outline             = Color(0xFF334155),
    outlineVariant      = Color(0xFF1E293B),
    error               = Color(0xFFF87171),
    onError             = Color(0xFF450A0A),
    errorContainer      = Color(0xFF450A0A),
    onErrorContainer    = Color(0xFFFECACA),
)
```

> `success`·`neutralNudge`·`difficulty`·힌트 위계색은 Material 슬롯에 1:1로 안 들어가므로 `BcsColors`(CompositionLocal)로 별도 노출한다. Material의 `error` 슬롯은 **파괴적 행동 전용**으로만 배선한다(오답 피드백에 배선 금지).

---

## 3. 타이포그래피 (Typography)

### 3.1 폰트
- **UI 본문: Pretendard.** 한글 가독성·웨이트 범위가 넓다. 웨이트 4종 번들: Regular(400)/Medium(500)/SemiBold(600)/Bold(700).
- **코드: 고정폭(monospace).** ⭐️ CS 문제·힌트에 코드 스니펫이 등장하므로(§도메인) 코드용 폰트를 별도로 둔다. 권장: **JetBrains Mono**(또는 한글 혼용 시 D2Coding). 미로딩 시 `FontFamily.Monospace` 폴백.
- **웹(Wasm) 로딩:** `.ttf`를 `composeResources/font/`로 **번들**하고 `Font(Res.font.*)`로 `FontFamily` 구성(CDN 의존 회피 — Wasm Canvas는 자체 래스터라이즈라 CDN CSS가 캔버스 텍스트에 반영되지 않음). 폴백: Pretendard→`SansSerif`, code→`Monospace`.

### 3.2 타입 스케일

| 토큰 | size(sp) | weight | lineHeight | 용도 |
|------|----------|--------|-----------|------|
| `displayL` | 24 | Bold | 32 | 온보딩·완료 대제목 |
| `titleL` | 20 | Bold | 28 | 화면 제목·섹션 큰 제목 |
| `headingM` | 18 | Bold | 26 | 섹션 제목·빈 상태 제목 |
| `headingS` | 17 | Bold | 24 | 헤더 타이틀 |
| **`question`** | 18 | SemiBold | 28 | ⭐️ **문제 질문 텍스트**(가독·집중 우선, 넉넉한 행간) |
| `bodyL` | 16 | Regular | 24 | 본문 설명·힌트 본문 |
| `bodyM` | 15 | Regular | 22 | 폼 입력 텍스트·카드 본문 |
| `bodyS` | 14 | Medium | 20 | 보조 본문·링크 |
| `label` | 14 | SemiBold | 20 | 폼 라벨·버튼 보조 |
| `caption` | 12 | Medium | 16 | 메타·캡션·헬퍼 |
| `captionBold` | 12 | Bold | 16 | 배지·태그 텍스트 |
| **`codeInline`** | 14 | Regular(mono) | 22 | ⭐️ 문장 내 코드 조각 `like_this` |
| **`codeBlock`** | 13 | Regular(mono) | 20 | ⭐️ 여러 줄 코드 블록(가로 스크롤 허용) |

```kotlin
val BcsTypography = Typography(
    displaySmall = TextStyle(fontFamily = Pretendard, fontWeight = Bold,     fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge   = TextStyle(fontFamily = Pretendard, fontWeight = Bold,     fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium  = TextStyle(fontFamily = Pretendard, fontWeight = Bold,     fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall   = TextStyle(fontFamily = Pretendard, fontWeight = Bold,     fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge    = TextStyle(fontFamily = Pretendard, fontWeight = Regular,  fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium   = TextStyle(fontFamily = Pretendard, fontWeight = Regular,  fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall    = TextStyle(fontFamily = Pretendard, fontWeight = Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge   = TextStyle(fontFamily = Pretendard, fontWeight = SemiBold, fontSize = 14.sp, lineHeight = 20.sp), // 버튼은 Bold 오버라이드
    labelMedium  = TextStyle(fontFamily = Pretendard, fontWeight = Medium,   fontSize = 12.sp, lineHeight = 16.sp),
)
// question / codeInline / codeBlock 은 Material 슬롯 밖 → BcsType 로 별도 노출
```

> 문제 질문(`question`)은 재생(직접 떠올림) 학습의 주인공이므로 별도 스타일로 크고 읽기 편하게. 버튼 라벨은 `fontWeight = Bold` 오버라이드.

---

## 4. 간격 / 라운드 / 그림자 / 모션

### 4.1 Spacing (4dp 배수)

| 토큰 | dp | 용도 |
|------|----|------|
| `space1` | 4 | 미세 간격 |
| `space2` | 8 | 칩 내부·작은 gap |
| `space3` | 12 | 카드 내부 행 간 |
| `space4` | 16 | 카드 패딩·입력 좌우 패딩 |
| `space5` | 20 | **콘텐츠 가로 패딩(표준)**·카드 패딩 |
| `space6` | 24 | 폼 필드 간·여유 패딩 |
| `space8` | 32 | 섹션 간 |
| `space10` | 40 | 큰 섹션 분리 |

표준: **콘텐츠 가로 패딩 20dp(`space5`)** 로 통일.

### 4.2 Radius

| 토큰 | dp | 적용 |
|------|----|------|
| `radiusSm` | 8 | 작은 배지·select |
| `radiusChip` | 12 | 힌트 레벨 칩·아이콘칩 |
| `radiusCard` | 16 | **카드·입력·버튼 기본** |
| `radiusSheet` | 32 | 바텀시트 상단 모서리 |
| `radiusFull` | 9999 | 태그·진행 점·둥근 요소 |

### 4.3 Elevation / Shadow

| 토큰 | elevation(dp) | 용도 |
|------|---------------|------|
| `elevCard` | 1 | 카드 기본 |
| `elevButton` | 4 | Primary 버튼 부양감 |
| `elevSheet` | 8 | 하단 고정 액션바·바텀시트·스낵바 |

> 다크 모드에서는 그림자 대신 `surface` 명도 차·`borderSubtle`로 표면 위계를 준다(다크에서 그림자는 잘 안 보임).

### 4.4 모션 (Motion)

| 토큰 | 값 | 적용 |
|------|----|----|
| `pressScale` | 0.98 | 버튼·카드 눌림 |
| `pressScaleStrong` | 0.96 | 강조 CTA(정답 제출) |
| `durFast` | 120ms | 색·스케일 트랜지션 |
| `durBase` | 200ms | 진입·포커스·힌트 펼침 |
| `durDrilldown` | 320ms | ⭐️ 디딤 문제 진입/복귀 — '내려갔다 올라오는' 깊이 전환(슬라이드+깊이) |
| easing | `FastOutSlowInEasing` | 기본 |

> 힌트 레벨 펼침은 `durBase` 부드러운 확장. 정답 순간은 `success` + 햅틱 + 짧은 축하 모션(과하지 않게). 모션은 **인지 보조 목적**만(원칙·UX 4).

---

## 5. 컴포넌트 스펙 (Components)

각 컴포넌트는 치수·색·상태(default/pressed/disabled/error)를 가진다. 모든 색·치수는 §2~§4 토큰만 참조한다.

### 5.1 버튼
- **PrimaryButton** — `fillMaxWidth`, height **56dp**, radius 16, 텍스트 `labelLarge`+Bold `onPrimary`. default bg `primary`(+`elevButton`); pressed bg `primaryPressed`+`pressScaleStrong`; disabled `primary` alpha .4; loading 시 16dp `CircularProgressIndicator`(onPrimary)+클릭 차단. (예: "정답 확인하기")
- **SecondaryButton** — bg `secondaryContainer`, 텍스트 `onSecondaryContainer`. height 56dp(인라인 40dp). (예: "조금 더 풀기")
- **GhostButton** — 투명 bg + 1dp `border`, 텍스트 `textLabel`. pressed bg `surfaceSubtle`. (예: "오늘은 여기까지")
- **TextLink** — 텍스트 `bodyS`+Bold `primary`. (예: "로그인")

### 5.2 답 입력 — AnswerTextField (주관식 단답)
- 구조: (라벨 생략 가능) → input box(height **56dp**, 좌우 16dp, radius 16, bg `surfaceSubtle`, border 1dp `border`, 입력 `bodyM`, placeholder `textTertiary`) → helper(`caption`).
- focus: border `primary`(+옅은 ring `primary` alpha .2 또는 생략). **자동 포커스**(UX 7).
- ⭐️ **불일치 상태에도 border를 `danger`(빨강)로 바꾸지 않는다.** 재시도 유도는 인접 `RetryNudge`(§5.6)로, 입력칸은 중립 유지. (무낙인)
- 키패드: 텍스트/영문 최적화. 제출은 IME action 또는 하단 버튼.
- Compose: `BasicTextField` + `decorationBox`로 56dp·색 정밀 제어.

### 5.3 카드 — BcsCard
- bg `surface`, padding 16~20dp, radius 16, border 1dp `borderSubtle`, shadow `elevCard`(다크는 border로 대체).
- 클릭형: `pressScale(0.98)` + ripple.
- 변형: **InfoCard**(힌트·안내) bg `primaryContainer`, border `primaryBorder`, 텍스트 `onPrimaryContainer`, 좌측 info/전구 아이콘 `primary`.

### 5.4 세션 진행 — SessionProgress
- ⭐️ **분량 기반**(예: `2 / 10` + 점/막대). **카운트다운 타이머 아님.**
- 점 인디케이터: 완료=`success`/`primary` 채운 점, 현재=`primary` 테두리, 남음=`border`. radius full.
- 담백하게 상단에. 압박 주지 않기.

### 5.5 힌트 — HintStepper + MisconceptionHintCard (⭐️ ByteCS 핵심)
- **HintButton**(진입점) — GhostButton/TextLink 톤 secondary. "힌트 보기". 사용자 요청(pull)으로만. **힌트가 없는 문제면 노출하지 않는다.**
- **HintStepper** — 약→강 순서로 하나씩 공개. 각 힌트는 카드/아코디언. **이미 연 힌트는 계속 보임**, "더 보기"로 다음 것. ⭐️ **문제별 자유 구성(리뷰 반영)** — 고정된 L1/L2 종류·개수 사다리가 아니다. 힌트 개수·종류는 문제마다 다르며(0~N개), 각 힌트는 '학습적'이어야 한다(풀이용 단순 실마리 지양). 강도 차이는 §6.1 위계색(옅음→info)으로 표현.
  - 선행 개념(배경지식) 힌트에는 내부에 **[더 쉬운 문제로 풀어보기]**(디딤 문제 진입) 버튼을 둘 수 있다.
- **MisconceptionHintCard**(오답 교정 힌트, push) — 특정 흔한 오답 제출 시 **자동** 표시되는 InfoCard(info 톤). "'프로세스'는 …라 이 문제의 답과는 달라요. 다시 도전!" ⭐️ **정답 비노출, danger 색 금지, 오답 낙인 아님**.

### 5.6 답 피드백 — CorrectFeedback / RetryNudge / NearMissNudge (⭐️ 무낙인·또렷)
- **CorrectFeedback**(정답) — `success` 액센트 + 체크 아이콘 + **햅틱** + **또렷한 짧은 축하 모션**(밋밋함 금지, 리뷰 반영). "맞았어요!" → (선택)더 알아보기 → 다음 문제. 이때 `ConceptChip`·해설 노출 가능.
- **RetryNudge**(불일치) — ⭐️ **경고 아닌 중립 인라인 넛지**(`neutralNudge`). 제출 순간 **분명한** 상태 변화를 주되 "아직이에요, 다시 해볼까요?" 톤. 빨강·경고 아이콘·토스트로 휙 사라짐 금지(설명이 남아야 함). 입력 유지·무제한 재시도.
- **NearMissNudge**(근접·오탈자, 리뷰 반영) — 불일치가 오탈자 수준으로 가까울 때 "거의 맞았어요, 오타를 확인해보세요"를 `info`/중립 톤으로. ⭐️ **정답·개념 비노출.** RetryNudge와 구별되는 별도 톤으로 '오타 때문임'을 알린다.

### 5.7 정답 공개·따라 입력·심화 — RevealAnswerButton / ModelAnswerBlock / TypeAlongField / EnrichmentBlock
- **RevealAnswerButton** — secondary(GhostButton/TextLink). **사용자 명시 요청 시에만**. "정답 보기".
- **ModelAnswerBlock** — 모범답안 + 짧은 해설(코드면 `codeBlock`). 도움 신호지만 **벌점처럼 보이게 하지 않는다**(중립·정보 톤).
- **TypeAlongField**(정답 따라 입력, 리뷰 반영) — ⭐️ 정답 공개 후 **모범답안을 직접 따라 입력해야 다음으로 진행**. AnswerTextField 재사용 + "정답을 따라 적어 볼까요?" 안내. '벌'이 아니라 '손으로 써 보며 익히기' 톤. 이 서비스가 진행을 요구하는 유일한 지점.
- **EnrichmentBlock**('더 알아보기' 심화 정보, 리뷰 반영) — 정답 처리 후 그 개념의 흥미로운 추가 정보를 **선택적으로** 펼치는 확장 카드(InfoCard 톤). 없으면 표시 안 함. 진행을 막지 않음.

### 5.8 파고들기 맥락 — DrilldownBadge / DrilldownBreadcrumb
- 디딤 문제로 내려간 상태에서만 상단에 노출. "〈원래 문제〉를 위한 더 쉬운 문제" 배지(info 톤) + **돌아가기**.
- **여러 겹 가능** → 스택 깊이 표현(브레드크럼/뒤로). 진입·복귀는 `durDrilldown` 깊이 전환.

### 5.9 개념 칩·난이도 — ConceptChip / DifficultyIndicator
- **ConceptChip** — 개념 태그(알약형 `radiusFull`, `caption`). ⭐️ **풀기 전 숨김**(정답 스포일 방지), 정답 확인·공개 이후에만 노출.
- **DifficultyIndicator** — 난이도 점/라벨(`difficulty` 색, 은은). 압박 금지.

### 5.10 빈 상태 — EmptyState (1급)
- 중앙 정렬: 아이콘(48~64dp, `surfaceSubtle` 원형 + `textTertiary`) + 제목(`headingM`) + 설명(`bodyS`,`textSecondary`) + Primary 액션.
- 카피: 비난·공허 금지, 다음 행동 제시(UX 9). 긍정 빈 상태("오늘 몫은 다 했어요!").

### 5.11 로딩 — Loading (1급)
- **스켈레톤 우선**(문제·카드 레이아웃 회색 박스 shimmer, bg `borderSubtle`). 스피너는 최소.
- 버튼 인라인 로딩은 §5.1.

### 5.12 스낵바·에러 — Snackbar / ErrorBanner (1급)
- **Snackbar** — 하단 부유, bg `textPrimary` .95, 텍스트 `surface` `bodyS`, radius 12, 우측 액션(`primary`). 3~4초. (성공/일반 안내)
- **ErrorBanner**(네트워크·시스템 오류) — "학습 기록은 안전해요"를 우선 고지 + 해결/재시도(GhostButton). 막다른 길 금지(UX 에러 가이드).
- ⭐️ **주의:** 여기서의 '에러'는 **시스템 오류**(네트워크 등)다. 사용자의 **오답은 에러가 아니다** → ErrorBanner/danger를 쓰지 않고 §5.6 RetryNudge로 처리.

### 5.13 확인 다이얼로그 — ConfirmDialog (파괴적 행동 전용)
- **계정 삭제** 등 되돌릴 수 없는 행동에만. bg `surface`, radius 16, padding 24dp. 제목 `headingM`, 설명 `bodyS`(무엇이 사라지는지 명시: "모든 학습 기록·숙련도·복습 일정이 삭제돼요").
- 액션: [취소] GhostButton + [삭제] PrimaryButton(containerColor=`danger`). ⭐️ danger 색은 여기서만 등장.

### 5.14 오류 신고 — ReportSheet (바텀시트)
- 03 문제 풀이에서 진입. 신고 유형 단일 선택 + 선택 서술 + [신고 보내기]. 완료 시 감사 톤. 학습 흐름 방해 금지(비동기). (§07 요구사항)

### 5.15 상단 바 — TopBar
- 세션/문제/폼: 좌측 back·나가기 + (해당 시) 맥락 타이틀(`headingS`). 파괴적이지 않은 이동은 경고 모달 없이.
- 홈: 인사 + 계정 진입점. 하단 내비는 최소(홈/계정) 또는 생략(모바일 우선).

### 5.16 스크랩·스트릭 — ScrapToggle / StreakBadge (리뷰 반영)
- **ScrapToggle** — 문제를 개인 북마크에 저장/해제하는 토글(별·북마크 아이콘, secondary). 켜짐=`primary`, 꺼짐=`textTertiary`. 문제 풀이 화면(03) 및 스크랩 목록에서 사용.
- **StreakBadge** — 연속 학습 표시(긍정 동기, 기능 6). 상승은 `streak`(불꽃) 톤. ⚠️ `streak` 토큰은 스트릭 표시에만 쓰고, 끊김에 `danger`·상실 공포 연출(불이 꺼진다 등)·죄책감 연출 금지 — 끊겨도 "다시 시작해요" 중립·격려 톤.

---

## 6. 도메인 시각 매핑 (Domain Visual Mapping)
> Resumaker의 '경험유형 5종 매핑'을 대체하는, CS한입 고유의 시각 규칙. 힌트 위계와 문제 상태를 색·톤으로 못박는다.

### 6.1 힌트 위계 (약 → 강 · 문제별 자유 구성)
⭐️ 고정 종류 사다리가 아니다(리뷰 반영). 순서상 위치로 강도를 나눈다.
| 위치 | 예시 | 배경 / 전경 | 톤 |
|------|------|------------|----|
| 약(앞쪽) | 가벼운 실마리·관점 | `surfaceSubtle` / `textSecondary` | 가장 옅음(스스로 떠올리게) |
| 강(뒤쪽) | 배경지식(선행 개념)·코드 예시 | `primaryContainer`(info) / `onPrimaryContainer` | 정보 카드 + (선행 개념이면) 디딤 문제 진입점 |
| (push) | 오답 교정 힌트 | `primaryContainer`(info) / `onPrimaryContainer` | 자동 표시, 정답 비노출 |

> 힌트는 모두 **info 계열(경고색 아님)**. 개수·종류는 문제마다 다르며, 강도 차이는 명도·순서로 표현한다. 각 힌트는 '학습적'이어야 한다.

### 6.2 문제 상태 색 매핑 (⭐️ 비처벌)
| 상태 | 색 토큰 | 신호 |
|------|--------|------|
| 미풀림/입력 중 | 뉴트럴(`surface`/`text*`) | 중립 |
| **정답** | `success` | 긍정·햅틱 |
| **불일치(재시도)** | `neutralNudge` | ⭐️ 중립·격려(빨강 금지) |
| **근접(오탈자)** | `info`/중립 | 안내('오타 확인', 정답 비노출) |
| 오답 교정 힌트 표시 | `info` | 안내(오답 낙인 아님) |
| 정답 공개·따라 입력 | `info`/뉴트럴 | 정보(벌점 아님) |

### 6.3 난이도
- 은은한 단계 표시(`difficulty` 색, 점/라벨). 디딤 문제는 '더 쉬움'을 암시. 압박·강조 금지.

> 아이콘은 웹폰트 의존을 피해 **Compose Material Icons**(`material-icons-extended`) 또는 동등 vector로. (힌트=`Lightbulb`, 정답=`Check`, 디딤=`SubdirectoryArrowRight`, 신고=`Flag` 등 — 구현에서 확정.)

---

## 7. 반응형 규칙 (Responsive) — 모바일 우선

⭐️ Resumaker는 웹 네이티브(1120/640dp 그리드)였으나, **CS한입은 모바일 우선**이다. 세로 단일 컬럼을 기본으로, 웹/태블릿은 **읽기 좋은 폭으로 중앙 제한**해 스케일업한다(다중 컬럼 그리드 불필요 — 화면당 초점이 '한 문제/한 세션').

- **기본(모바일 세로):** 단일 컬럼, 콘텐츠 가로 패딩 20dp(`space5`). 주요 CTA는 **엄지 영역 하단 고정·확장**(UX 6).
- **콘텐츠 최대 폭:** 태블릿/웹에서 콘텐츠 컨테이너를 **max-width 600dp**(`BcsSize.contentMax`)로 중앙 제한(문제/입력/읽기 가독, 한글 행당 40~50자). 바깥 여백은 `background`.
- **터치 타깃:** 최소 48dp. 버튼/칩 충분한 히트영역.
- **브레이크포인트(간소):**

  | 구간 | 폭 | 레이아웃 | 좌우 패딩 |
  |------|-----|----------|-----------|
  | Compact | < 600px | 단일 컬럼, 가용폭 | 20dp |
  | Medium+ | ≥ 600px | 단일 컬럼 max 600dp 중앙 | 24dp |

- **CTA 위치:** 모바일은 하단 고정(엄지영역). 웹 데스크톱은 콘텐츠 하단 인라인 또는 하단 고정 바 유지(모바일 일관성 우선).
- **스크롤:** 화면 내부 `verticalScroll`. 코드 블록은 **모바일 폭에서 가로 스크롤 없이 읽히도록 짧은 줄로 큐레이션**(리뷰 반영 — 가로 스크롤이 문제 컨텍스트를 끊음); 부득이한 긴 코드만 가로 `horizontalScroll` 컨테이너로 격리하고 본문 흐름은 유지.
- **글자 확대 대응:** OS 글자 최대 확대에도 안 깨지게(UX 5). 필요 시 바텀시트 등으로 전환.
- 구현: `BoxWithConstraints`로 폭 측정 → 공통 `BcsScaffold(topBar, contentMax, bottomCta, content)`가 상단 바 + 중앙 컨테이너 + 하단 CTA + 스낵바를 한 곳에서 그린다.

---

## 8. 화면별 적용 (Screens)

각 화면의 상세 요구사항은 `docs/design/01~07`에 있다. 이 절은 그 화면들이 **어떤 컴포넌트·토큰으로 조립되는지**만 연결한다. (백엔드·API·DB는 미확정이므로 엔드포인트·DTO는 기재하지 않는다 — 확정 후 별도 매핑.)

| 화면(문서) | 주요 컴포넌트 | 컨테이너 |
|---|---|---|
| 01 온보딩·시작 | 브랜드 헤더, 메타포 그래픽, PrimaryButton("바로 시작하기"), TextLink | 600dp 중앙 |
| 02 홈(오늘의 한입) | 인사 헤더, SessionProgress, 상태별 PrimaryButton(시작/이어서/완료), 계정 진입점, (게스트) 가입 배너 | 600dp 중앙 |
| **03 문제 풀이(히어로)** | TopBar+DrilldownBadge, SessionProgress, question 텍스트, ConceptChip(숨김), AnswerTextField, PrimaryButton, CorrectFeedback/RetryNudge, HintStepper, MisconceptionHintCard, RevealAnswerButton+ModelAnswerBlock, ReportSheet 진입 | 600dp 중앙 |
| 04 세션 완료 | 축하 헤더+메타포, 요약, PrimaryButton("오늘은 여기까지")/Secondary("조금 더 풀기"), (게스트) 승계 유도 | 600dp 중앙 |
| 05 로그인·가입 | 헤더, AnswerTextField(이메일/비번 재사용 스타일), PrimaryButton, TextLink(로그인↔가입 전환), Snackbar(실패) | 600dp 중앙 |
| 06 계정·설정 | 계정 상태 카드, 테마 토글(라이트/다크/시스템), 로그아웃, 계정 삭제(ConfirmDialog·danger) | 600dp 중앙 |
| 07 콘텐츠 오류 신고 | ReportSheet(바텀시트): 유형 단일 선택 + 서술 + PrimaryButton + 감사 피드백 | 바텀시트 |

### 8.1 히어로 와이어프레임 — 03 문제 풀이 (구성 예시)
```
┌── TopBar: [←나가기]      2 / 10     [⋯ 신고] ──┐  SessionProgress(분량)
│  (디딤 상태면) 〈원래 문제〉를 위한 더 쉬운 문제 ↩ │  DrilldownBadge
├──────────────[ 600dp 중앙 ]──────────────┤
│  Q. 서로 다른 키가 같은 버킷으로 매핑되는     │  question (18/SemiBold)
│     현상을 부르는 용어는?                    │
│                                           │
│  ┌ 답 입력 ───────────────────────┐        │  AnswerTextField (자동 포커스)
│  └──────────────────────────────┘        │
│                                           │
│  (불일치 시) 아직이에요, 다시 해볼까요?       │  RetryNudge (neutral·비처벌)
│  (흔한 오답 시) ┌ 💡 '프로세스'는 …라 달라요 ┐│  MisconceptionHintCard (info·정답 비노출)
│               └─────────────────────────┘ │
│                                           │
│  [ 힌트 보기 ]        [ 정답 보기 ]          │  HintButton / RevealAnswerButton (secondary)
├───────────────────────────────────────────┤
│  [        정답 확인하기        ]            │  PrimaryButton (엄지영역 하단 고정)
└───────────────────────────────────────────┘
힌트 열면 ↓ HintStepper: L1 키워드 → (더 보기) L2 배경지식 + [더 쉬운 문제로]
정답이면 ↑ CorrectFeedback(success+햅틱) → ConceptChip 노출 → 다음 문제
```

---

## 9. 검토 체크리스트 (디자인 충실도)

코드가 이 시스템을 따랐는지 PASS/FAIL로 본다.

- [ ] 모든 색이 `BcsColors`/ColorScheme 토큰 참조(raw hex 0건).
- [ ] 모든 dp가 `BcsSpacing`/`BcsRadius` 토큰 참조(raw dp 0건, 레이아웃 산식 예외).
- [ ] **라이트·다크 두 스킴 모두 구현·전환 동작**(시스템 설정 + 06 토글).
- [ ] ⭐️ **오답·불일치에 빨강/경고색 0건**(RetryNudge=neutral, danger는 계정 삭제에만).
- [ ] primary=blue 단일 브랜드색, 정답=success(emerald)만 별도 긍정 액센트.
- [ ] 카드·입력·버튼 radius=16, 칩=12, 태그=full, 바텀시트 상단=32. 입력·Primary 버튼 height 56dp.
- [ ] Pretendard(UI) + monospace(코드) 적용 또는 폴백 동작.
- [ ] 힌트가 §5.5/§6 위계(L1 키워드 < L2 배경지식, push 오답 교정)로, 모두 info 톤·정답 비노출.
- [ ] 디딤 문제 진입/복귀가 파고들기 맥락 배지 + 깊이 전환으로, 여러 겹 복귀 동작.
- [ ] 세션 진행이 **분량 기반**(카운트다운 타이머 0건), 정답 시 햅틱.
- [ ] ConceptChip이 풀기 전 숨김(정답 스포일 0건).
- [ ] Empty/Loading(스켈레톤)/Error 1급 구현, 막다른 길 0건. 사용자 오답을 시스템 에러로 다루지 않음.
- [ ] 콘텐츠 컨테이너 max-width 600dp 중앙, 모바일 주요 CTA 하단 고정.
- [ ] 각 화면이 §8·`01~07` 요구사항 구조·컴포넌트와 일치.
- [ ] 접근성: 글자 확대·스크린리더 대체텍스트·포커스 순서(문제→입력→제출→힌트).
- [ ] 힌트가 문제별 자유 구성(고정 L1/L2 사다리 0건)·모두 info 톤·정답 비노출.
- [ ] 근접(오탈자) 신호가 RetryNudge와 구별되는 톤으로 표시되고 정답 비노출.
- [ ] 정답 공개 후 따라 입력(TypeAlongField) 통과해야 진행, 정답 처리 후 [더 알아보기] 선택 노출.
- [ ] 스크랩 토글·목록, 지난 문제 다시 보기 동작. 완료 화면 축하 연출 또렷.
- [ ] 오답·불일치·근접·스트릭 끊김에 빨강/경고/죄책감 연출 0건.
