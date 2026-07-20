# 웹 클라이언트(Compose Multiplatform wasmJs) 구현 계획

이 문서는 "현재 모바일 애플리케이션(CS한입)을 웹 브라우저에서도 이용할 수 있게 만드는" 작업의 구현 계획이다.
**독자는 이 계획을 실행할 새 AI 세션**이다. 이 문서와 `docs/` 하위 지침만으로 전체 작업을 수행할 수 있어야 한다.

> **문서 상태**: 오너와의 합의로 확정된 계획 (2026-07-20). '§2 확정된 결정'의 항목은 재논의 없이 그대로 구현한다.
> 구현 중 이 계획과 기존 지침·명세가 충돌하면, 작업을 멈추고 오너에게 되묻는다(CLAUDE.md 반문 규칙).

---

## 1. 목적과 배경

- **핵심 목적**: 앱스토어 출시라는 복잡한 과정을 거치지 않고, 여러 테스터에게 URL 하나로 서비스를 소개하고 피드백을 받는다.
- **부가 목적**: PC 환경에서도 학습하고 싶은 사용자를 위한 멀티 플랫폼 서비스 구축.
- **오너 요구(중요)**: 웹 클라이언트는 실제 웹 사용자를 위해 존재한다. 모바일 화면의 단순 이식이 아니라, **웹/데스크톱 환경을 고려한 화면 구성과 비율(적응형 레이아웃)**을 갖춰야 한다.
- CLAUDE.md 기술 스택에 "Web: Compose Multiplatform (Wasm/Canvas)"가 이미 선언되어 있고, 도메인 명세서 로드맵에도 "모바일/웹 클라이언트 확장"이 명시되어 있다. 이 계획은 그 선언을 실체화한다.

## 2. 확정된 결정 (오너 결정 2026-07-20)

1. **배포: Spring 서버가 웹 번들을 같은 오리진에서 서빙한다.** 빌드된 Wasm 정적 번들을 Spring Boot 정적 리소스로 포함해 `/`에서 서빙한다. CORS·mixed-content 문제가 원천 차단되고, 배포 유닛이 서버 하나로 통일되어 테스터에게 URL 하나만 주면 된다. (별도 정적 호스팅 + CORS 방식은 채택하지 않음 — 필요해지면 백로그.)
2. **브라우저 지원: wasmJs 단독.** WasmGC를 지원하는 최신 브라우저(Chrome 119+, Firefox 120+, Safari 18.2+)만 지원한다. Kotlin/JS 폴백 타깃은 만들지 않는다. 미지원 브라우저에는 안내 문구만 표시한다(§7-5).
3. **토큰 저장: 브라우저 localStorage 수용.** 현행 `TokenStore`(multiplatform-settings)를 그대로 사용하며, wasmJs에서는 localStorage 평문 저장이 된다. 근거: 외부 스크립트가 없는 Wasm 앱이라 XSS 표면이 작고, JWT 만료 24시간이 피해 상한이며, 테스터 MVP 단계에 적절. 보안 강화(httpOnly 쿠키 등)는 백로그.
4. **화면 구성: WindowSizeClass 기반 적응형 레이아웃.** 넓은 화면에서 구조 자체를 재배치한다(2패널 등). 모바일 코드는 compact 분기로 그대로 재사용한다. 상세는 §6.

## 3. 현재 상태 (as-is, 2026-07-20 조사 결과)

### 3.1 요약: 이식에 유리한 구조다

UI/로직 전체(62개 .kt)가 `app/shared`의 commonMain에 응집되어 있고, 플랫폼 의존은 expect/actual **3개 파일(선언 4개)**로 격리되어 있다. TTS·햅틱·알림·딥링크·카메라 등 무거운 플랫폼 API는 사용하지 않는다. commonMain에 `import android.*`/`import java.*`는 0건이라 wasmJs 컴파일을 막는 코드가 없다.

| 항목 | 현재 상태 |
|---|---|
| KMP 타깃 | `androidLibrary`(신규 AGP KMP 플러그인) + `iosArm64` + `iosSimulatorArm64` + `jvm()`. **웹 타깃·웹 설정 전무**(wasmJs/js/webpack 관련 문자열 0건) |
| `jvm()` 타깃의 용도 | 배포용이 아니라 **Compose UI 테스트 실행기**(`:app:shared:jvmTest`, skiko + compose.uiTest로 헤드리스 컴포지션) |
| 버전 | Kotlin **2.4.0**, Compose Multiplatform **1.11.1**(JetBrains 배포판 → wasm 정식 지원), Ktor 3.2.2, multiplatform-settings 1.3.0 |
| 모듈 | `:app:androidApp`(런처) / `:app:shared`(본체) / `:core`(사실상 빈 공용 모듈, shared·server 양쪽이 `api`로 의존) / `:server` |
| expect/actual | ① `Platform.kt` `getPlatform()`(데모 잔재, 실사용처 없음) ② `problem/data/HttpClientFactory.kt` `defaultHttpClientEngine()` ③ 같은 파일 `platformApiBaseUrl()` ④ `SystemBackHandler.kt`(iOS/JVM은 no-op) |
| 네트워킹 | Ktor. 각 `Ktor*Repository`가 `"$baseUrl/api/..."` 문자열 연결로 URL 구성, `baseUrl` 기본값 = `platformApiBaseUrl()`. 인증은 `AuthHttpClientFactory`의 `HostScopedAuth` 플러그인이 전송 시점마다 토큰을 읽어 `Authorization: Bearer` 부착, `App.kt:518`의 `Url(platformApiBaseUrl()).host`로 호스트 스코프 제한 |
| 로컬 저장 | multiplatform-settings no-arg `Settings()` 3곳: `TokenStore`(JWT), `OnboardingStore`, `ThemeController`. wasmJs에서는 localStorage(StorageSettings)로 매핑됨 |
| 내비게이션 | 라이브러리 없음. `App.kt`의 `sealed interface Screen` + 명시적 백스택. 플랫폼 의존은 `SystemBackHandler` 하나로 격리 |
| "낭독" | TTS가 아니라 **Compose 접근성 시맨틱**(`liveRegion` 등, commonMain) — 웹 추가 작업 없음 |
| 레이아웃 | `WindowSizeClass`/`BoxWithConstraints` 미사용. `BcsDimens.contentMax` 폭 상한 + 세로 스크롤 구조. 하드코딩 폭은 없어 넓은 화면에서 깨지진 않지만 중앙 정렬만 됨 |
| 서버 인증 | 무상태 JWT(HS256) `Authorization: Bearer` 헤더. 게스트도 서버 User(role=GUEST)로 발급, 신원=JWT. 쿠키·세션·CSRF 미사용(→ 브라우저 이식에 유리) |
| 서버 시큐리티 | `SecurityConfig.kt` 단일 catch-all 체인, **`anyRequest authenticated`** → 정적 리소스 permit 없이는 `/`가 401. `/admin/**`은 별도 체인(`AdminSecurityConfig`, @Order(0), 폼 로그인) |
| CORS | **설정 전무** — 같은 오리진 서빙(결정 1)이라 추가하지 않는다 |
| 정적 서빙 | 커스텀 설정 없음(부트 기본값 `classpath:/static/` 서빙은 살아 있음) |
| DTO | 서버와 공유하지 않음. 클라 독자 wire DTO(@Serializable, ignoreUnknownKeys) |
| compose resources | `composeResources/drawable/compose-multiplatform.xml` 1개(템플릿 잔재), 코드 사용처 0건 |
| API baseURL 주입 | `GenerateApiConfigTask`가 `local.properties`의 `bytecs.apiBaseUrl`을 androidMain 전용 `ApiConfig.kt`로 생성(Android 전용 메커니즘, 웹과 무관) |

### 3.2 클라이언트가 호출하는 서버 API (전부 `/api/**`, 변경 불필요)

게스트 발급 `POST /api/guests` · 가입/로그인 `POST /api/auth/*` · 내 정보/설정/삭제 `GET·PATCH·DELETE /api/users/me*` · 오늘의 세션 `GET /api/sessions/today` + `next/attempts/reveal/hints/reveal/items/{position}` · 신고 `POST /api/problems/{id}/reports` · 스크랩 `POST·DELETE /api/problems/{id}/scraps`, `GET /api/scraps*` · 카테고리 이력 `GET /api/learning-history/categories`. 전부 상대 경로 규칙이 일관되어 같은 오리진 서빙과 그대로 맞물린다.

### 3.3 사전 정리 사항 (구현 세션 참고)

`app/shared/src/commonMain/kotlin/watson/bytecs/ui/theme/.omc/` 아래에 OMC 에이전트 상태 파일이 실수로 생성되어 있다. `.gitignore`의 `.omc/` 규칙으로 git에는 안 잡히지만 디스크에서 삭제하고 시작할 것(소스 아님).

## 4. 목표 아키텍처

```
:core ───────────────┐ (wasmJs 타깃 추가 — :app:shared가 api로 물고 있어 필수)
:app:shared ─────────┤ (wasmJs 타깃 추가: commonMain 전체 + wasmJsMain actual 3파일)
  ├ androidMain      │
  ├ iosMain          │
  ├ jvmMain(테스트용) │
  └ wasmJsMain(신규)  │
:app:androidApp      │ Android 런처 (불변)
:app:webApp(신규) ───┘ 웹 런처: wasmJs main() + ComposeViewport { App() } + index.html
:server              ← :app:webApp 산출물(정적 번들)을 static/으로 포함해 같은 오리진 서빙
```

**모듈 구조 결정: 웹 엔트리포인트는 `:app:webApp` 신규 모듈로 분리한다.** `:app:shared`에 `binaries.executable()`을 직접 넣는 방식보다, ① `:app:androidApp`(런처)/`:app:shared`(라이브러리) 기존 대칭 구조와 일치하고 ② shared가 순수 라이브러리로 남아 신규 AGP KMP 플러그인(`androidLibrary`)과 실행 바이너리 설정의 간섭 여지를 없애며 ③ 웹 전용 리소스(index.html, 로딩 화면, webpack 설정)의 자리가 명확해진다.

**개발 루프와 배포 루프를 분리한다.**
- 개발: `:app:webApp:wasmJsBrowserDevelopmentRun`(webpack dev server) + `/api` 프록시 → 로컬 Spring(8080)으로 전달. 핫 리로드 유지.
- 배포: `:app:webApp:wasmJsBrowserDistribution` 산출물을 `:server` 리소스(`static/`)로 복사 → `bootJar` 하나가 웹+API 전체.

## 5. 단계별 구현 계획

각 단계는 CLAUDE.md 커밋 규칙(서비스 온전 동작, 테스트 통과, 작업 단위별 커밋)을 따른다. 기준선: 현재 server 346·jvmTest 391 실패 0 — 매 커밋 전 전체 테스트 green + wasmJs 컴파일(`:app:shared:compileKotlinWasmJs`) 확인.

### 1단계 — wasmJs 타깃·actual·웹 부팅 (+ 한글 IME 게이트)

**변경 파일:**

1. `gradle/libs.versions.toml` — 추가:
   ```toml
   ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
   kotlinx-browser = { module = "org.jetbrains.kotlinx:kotlinx-browser", version = "0.3" }
   ```
   (`kotlinx-browser`는 wasmJs에서 `kotlinx.browser.window`/`document` 접근에 필요. `0.3`은 Kotlin 2.4 호환 제안값 — 구현 시점에 더 최신 안정판이 있으면 교체.)

2. `core/build.gradle.kts` — 타깃 추가:
   ```kotlin
   @OptIn(ExperimentalWasmDsl::class)
   wasmJs { browser() }
   ```
   (Kotlin 2.4의 `ExperimentalWasmDsl` import 경로는 `org.jetbrains.kotlin.gradle.ExperimentalWasmDsl`.)

3. `app/shared/build.gradle.kts` — 타깃 + wasmJsMain 의존성:
   ```kotlin
   @OptIn(ExperimentalWasmDsl::class)
   wasmJs { browser() }
   // sourceSets:
   wasmJsMain.dependencies {
       implementation(libs.ktor.client.js)   // wasmJs용 Ktor 엔진
       implementation(libs.kotlinx.browser)
   }
   ```
   `multiplatform-settings(-no-arg)`는 commonMain 의존이라 자동 전파된다(§7-3 검증 항목).

4. `app/shared/src/wasmJsMain/kotlin/watson/bytecs/` — actual 3파일:
   - `Platform.wasmJs.kt` — `actual fun getPlatform(): Platform` → `"Web (Kotlin/Wasm)"` (형식상 필요, 실사용처 없음).
   - `problem/data/HttpClientFactory.wasmJs.kt`:
     ```kotlin
     actual fun defaultHttpClientEngine(): HttpClientEngine = Js.create()
     // 같은 오리진 서빙(오너 결정 1)이 전제 —
     // 레포지토리들이 "$baseUrl/api/..." 로 URL을 만들고 App.kt가 Url(...).host 로 토큰 스코프를 잡으므로
     // 절대 URL인 window.location.origin 이 정합하다. (dev server에서는 프록시가 /api를 서버로 전달)
     actual fun platformApiBaseUrl(): String = window.location.origin
     ```
   - `SystemBackHandler.wasmJs.kt` — no-op actual (iOS/JVM과 동일 트레이드오프, 주석으로 명시. popstate 연동은 백로그 §8).

5. `:app:webApp` 신규 모듈:
   - `settings.gradle.kts`에 `include(":app:webApp")`.
   - `app/webApp/build.gradle.kts` — 플러그인: `kotlinMultiplatform` + `composeMultiplatform` + `composeCompiler`. 타깃:
     ```kotlin
     @OptIn(ExperimentalWasmDsl::class)
     wasmJs {
         browser { commonWebpackConfig { outputFileName = "bytecs.js" } }
         binaries.executable()
     }
     // wasmJsMain.dependencies { implementation(projects.app.shared); implementation(compose.ui) ... }
     ```
   - `app/webApp/src/wasmJsMain/kotlin/watson/bytecs/Main.kt`:
     ```kotlin
     @OptIn(ExperimentalComposeUiApi::class)
     fun main() {
         ComposeViewport(document.body!!) { App() }
     }
     ```
   - `app/webApp/src/wasmJsMain/resources/index.html` — 캔버스 전체 화면 스타일 + `bytecs.js` 로드. **정확한 부트스트랩 마크업은 CMP 1.11.1 공식 웹 템플릿(kmp.jetbrains.com 생성물)을 참조해 맞출 것**(버전별로 로더 스크립트 구조가 달라 이 문서에 고정하지 않는다).

**검증(이 단계의 게이트):**
- `gradlew :app:webApp:wasmJsBrowserDevelopmentRun`으로 브라우저 부팅, 온보딩/홈 렌더 확인.
- **한글 IME 게이트(최우선 리스크 §7-1)**: 문제 풀이 답변 `TextField`에 한글 조합 입력(받침·조합 중 백스페이스 포함)이 정상 동작하는지 실제 브라우저(Chrome + 가능하면 Safari)에서 확인한다. 주관식 한글 답변이 서비스의 핵심 입력이므로, **여기서 결함이 발견되면 진행을 멈추고 오너에게 보고**한다(대응 후보: CMP 패치 버전 업그레이드, JetBrains 이슈 확인).
- 기존 전 테스트 green(`:server:test`, `:app:shared:jvmTest` 등) — 기존 타깃에 회귀 없음 확인.
- 커밋: `feat: 웹(wasmJs) 타깃과 브라우저 엔트리포인트 추가`
  - 참고: actual 3파일은 선언 위임뿐이라 신규 단위 테스트 대상 로직이 없다. "기존 전 타깃 테스트 green + wasmJs 컴파일"이 이 커밋의 테스트 근거임을 커밋 본문에 남긴다.

### 2단계 — 개발 프록시 구성과 통신 스모크

1. `app/webApp/webpack.config.d/dev-proxy.js` — dev server에서 `/api`(및 필요 시 `/h2-console`)를 `http://localhost:8080`으로 프록시:
   ```js
   // wasmJsBrowserDevelopmentRun 전용. 배포 번들에는 영향 없음.
   // ⚠️ webpack devServer 기본 포트도 8080이라 로컬 Spring(8080)과 충돌한다 — dev server는 반드시 다른 포트를 명시한다.
   //    (토큰 호스트 스코프는 host만 비교하므로 포트가 달라도 정상 동작)
   config.devServer = Object.assign(config.devServer || {}, {
       port: 8081,
       proxy: [{ context: ["/api"], target: "http://localhost:8080" }],
   });
   ```
   (webpack 버전에 따라 proxy 스키마가 객체/배열로 다르므로 구동해 확인.)
2. 로컬 Spring(`:server:bootRun`, local 프로파일) + dev server를 띄우고 **수동 스모크**: 게스트 자동 발급 → 오늘의 세션 시작 → 정답/오답 제출 → 힌트 공개 → 스크랩 추가/목록 → 카테고리 이력 → **새로고침 후 토큰·학습 상태 유지**(localStorage 검증) → 브라우저 DevTools에서 `Authorization: Bearer` 헤더 부착 확인.
3. 커밋: `chore: 웹 개발 서버에 API 프록시 구성` (스모크 결과를 커밋 본문에 기록.)

### 3단계 — Spring 서버의 같은 오리진 서빙 통합

1. **번들 → 서버 리소스 연결** (`server/build.gradle.kts`):
   ```kotlin
   import org.gradle.language.jvm.tasks.ProcessResources

   // 웹 배포 번들 포함은 -PincludeWeb 게이트 뒤에 둔다.
   // ⚠️ 게이트 없이 processResources에 걸면 :server:test 가 매번 웹 번들 빌드를 기다리게 된다(§7-7 회귀 금지).
   // 크로스 프로젝트 task 직접 참조 대신 산출물 디렉터리를 명시적으로 배선한다.
   if (project.hasProperty("includeWeb")) {
       tasks.named<ProcessResources>("processResources") {
           from(project(":app:webApp").layout.buildDirectory.dir("dist/wasmJs/productionExecutable")) {
               into("static")
           }
           dependsOn(":app:webApp:wasmJsBrowserDistribution")
       }
   }
   ```
   사용법: `gradlew -PincludeWeb :server:bootRun`(통합 확인) / `gradlew -PincludeWeb :server:bootJar`(배포). 게이트 없는 `:server:test`·`:server:bootRun`은 웹 빌드 없이 기존 속도로 돈다.
   구현 시 확인할 것: ① 산출물 경로가 CMP 1.11.1에서 `build/dist/wasmJs/productionExecutable`인지 실제 빌드로 확인, ② 대안(bootJar 태스크에만 from을 거는 방식)과 비교해 배포 실수 여지(게이트 누락 시 웹 없는 jar) vs 단순성 트레이드오프를 판단해 유지·교체 — 어느 쪽이든 일상 서버 개발 루프가 웹 빌드를 기다리지 않아야 한다는 원칙은 불변.
2. **SecurityConfig 정적 리소스 permit** (`server/src/main/kotlin/watson/bytecs/config/SecurityConfig.kt`): `anyRequest authenticated` **앞에** 추가:
   ```kotlin
   // 웹 클라이언트 정적 번들(같은 오리진 서빙). GET만 열어 서빙 외 표면을 만들지 않는다.
   authorize(HttpMethod.GET, "/", permitAll)
   authorize(HttpMethod.GET, "/index.html", permitAll)
   authorize(HttpMethod.GET, "/*.js", permitAll)
   authorize(HttpMethod.GET, "/*.wasm", permitAll)
   authorize(HttpMethod.GET, "/*.css", permitAll)
   authorize(HttpMethod.GET, "/*.map", permitAll)
   authorize(HttpMethod.GET, "/composeResources/**", permitAll)
   authorize(HttpMethod.GET, "/favicon.ico", permitAll)
   ```
   주의: `/*.js`·`/*.wasm` 패턴은 **루트 레벨만** 매칭한다(중첩 경로 미포함). 배포 번들은 대개 루트에 평평하게 떨어지지만, 실제 산출물 파일 구성(폰트·skiko 리소스·중첩 디렉터리 유무)을 보고 패턴을 확정한다. 광범위 `/**` permit은 금지 — `/api` 보호가 기본값(`anyRequest authenticated`)으로 유지되어야 한다.
3. **`.wasm` MIME 확인**: 서빙된 `.wasm`의 `Content-Type`이 `application/wasm`인지 확인(브라우저 스트리밍 컴파일 요건). Boot 기본 매핑에 없으면 `WebServerFactoryCustomizer`로 `MimeMappings`에 추가.
4. **테스트(Spring 테스트 가이드 준수)**: MockMvc — ① `GET /` 200 + HTML(번들이 테스트 리소스에 없어도 되도록, permit 규칙 자체는 시큐리티 슬라이스로 검증 가능) ② 기존 보호 경로 401 회귀(`GET /api/users/me` 등) ③ `/admin` 체인 무영향.
5. **통합 확인**: `:server:bootRun`(웹 포함 게이트 켜서) 후 `http://localhost:8080/` 접속 → 프록시 없이 전체 플로우 재수행(2단계 스모크와 동일 체크리스트).
6. 커밋: `feat: Spring 서버가 웹 클라이언트 번들을 같은 오리진에서 서빙`

### 4단계 — 웹을 고려한 적응형 레이아웃 (오너 결정 4의 실체화)

**4-1. 창 너비 클래스 도입 (기반 커밋)**

- Material3의 `material3-window-size-class` 아티팩트를 쓰지 않고 **자체 경량 구현**을 택한다. 근거: 이 프로젝트는 수동 DI·자체 내비게이션 등 라이브러리 최소주의를 일관되게 유지하고 있고, 필요한 것은 너비 3단 분류뿐이다. (아티팩트 채택이 유리해지는 시점 — 예: pane scaffolding 필요 — 이 오면 그때 교체를 오너에게 제안.)
- `app/shared/src/commonMain/.../ui/layout/WindowWidthClass.kt`(신규):
  ```kotlin
  enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

  /** Material3 기준 브레이크포인트: <600dp COMPACT, <840dp MEDIUM, 이상 EXPANDED. 순수 함수로 분리해 테스트한다. */
  fun windowWidthClassFor(widthDp: Float): WindowWidthClass = ...

  val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.COMPACT }
  ```
- `App.kt` 최상위에서 `BoxWithConstraints`(또는 `LocalWindowInfo`)로 폭을 측정해 `LocalWindowWidthClass` 제공. 기본값 COMPACT → 기존 모바일 렌더와 100% 동일(회귀 없음).
- 테스트: `windowWidthClassFor` 경계값(599/600/839/840) commonTest + jvmTest에서 `compose.uiTest`로 창 크기를 강제해 CompositionLocal 전파 검증(기존 jvmTest 패턴 재사용).
- 커밋: `feat: 창 너비 클래스 도입으로 적응형 레이아웃 기반 마련`

**4-2. 화면별 확장 레이아웃 (화면 단위 커밋 분할)**

UX 지침(`docs/ux/UX 핵심 가이드.md` 인지 부하 최소화)을 웹에도 그대로 적용한다 — 넓다고 정보를 더 채우는 게 아니라, **가독폭 유지 + 이동(목록↔상세) 횟수 축소**가 목적이다.

| 화면 | COMPACT(현행 유지) | EXPANDED(≥840dp) 목표 | 커밋 |
|---|---|---|---|
| 홈(오늘의 세션) | 세로 스크롤 | 세션 시작 카드를 주 영역에, 스트릭·진행 요약을 보조 컬럼에 두는 2컬럼. 과밀 금지 — 여백 유지 | `feat: 홈 화면 확장 레이아웃` |
| 문제 풀이(SessionScreen) | 현행 | **재배치보다 가독폭**: 본문·입력·피드백을 가독폭(≈720dp)으로 중앙 유지. 힌트/더 알아보기 사이드 패널화는 이번 범위에서 제외(문제 풀이 몰입 흐름 훼손 위험 — 백로그) | `feat: 문제 풀이 화면 웹 가독폭 적용` |
| 스크랩 목록/상세 | 목록→상세 2단 이동 | 리스트-디테일 2패널(좌 목록, 우 상세). 기존 목록·상세 컴포저블 재사용, 선택 상태만 상위로 끌어올림 | `feat: 스크랩 화면 리스트-디테일 2패널` |
| 카테고리 이력 목록/상세 | 목록→상세 2단(스크랩과 동일 구조로 통일됨 — 커밋 4667c2c) | 스크랩과 같은 2패널 패턴 재사용(공통 2패널 컴포저블 추출 검토) | `feat: 카테고리 이력 화면 리스트-디테일 2패널` |
| 계정/설정·로그인/가입·온보딩 | 현행 | 중앙 카드(contentMax) — 이미 근접, 폭·여백만 정돈 | (위 커밋들에 편승 또는 소커밋) |
| 세션 완료 | 현행 | 중앙 카드 | 〃 |

- MEDIUM(600~839dp)은 COMPACT 렌더 + 여백 확대를 기본으로 하고, 화면별로 2패널이 자연스러우면 개별 판단(문서화).
- 2패널에서 상세 선택 시 **백스택에 push하지 않고** 패널 내 상태로 처리한다(COMPACT에서만 push). `SystemBackHandler`/뒤로가기 동작이 두 모드에서 일관되도록 각 화면 ViewModel이 아니라 화면 컴포저블 수준에서 분기한다. 상세 ViewModel 캐시 키(`detailViewModelKey`) 유일성 회귀 테스트(커밋 f489452)가 2패널에서도 성립하는지 확인.
- 각 커밋마다 jvmTest로 EXPANDED 크기 강제 UI 테스트(2패널 동시 렌더, 선택 동작) 추가.

**4-3. 웹 입력 폴리시(같은 단계에서 확인, 결함 시 소커밋)**
- 답변 입력에서 Enter 제출 동작 확인(기존 keyboardActions 유무 확인 후 필요 시 추가), 버튼 포커스 이동(Tab) 및 포커스 표시, 마우스 휠 스크롤·텍스트 선택 동작 확인.

### 5단계 — 마감: 로딩 경험·미지원 브라우저 안내·문서 갱신

1. **index.html 로딩 화면**: Wasm 번들은 수 MB라 첫 로드에 수 초 걸린다. 순수 HTML/CSS 로딩 인디케이터(서비스명 + 스피너)를 두고 캔버스 준비 시 제거.
2. **미지원 브라우저 안내**: `WebAssembly` GC 지원을 감지(공식 템플릿의 feature-detect 스니펫 참조)해, 미지원이면 "최신 Chrome/Firefox/Safari에서 이용해 주세요" 정적 문구 표시.
3. **타이틀·파비콘**: `<title>CS한입</title>` + 파비콘.
4. **문서 갱신(지침 개선 규칙에 따름)**:
   - `docs/dev/KMP 개발 가이드.md`에 웹(wasmJs) 절 추가: 타깃 구조, actual 작성 규칙, 개발 루프(`wasmJsBrowserDevelopmentRun` + 프록시), 배포 루프(서버 서빙), 한글 IME 확인 의무.
   - `docs/도메인 명세서.md` 로드맵의 "모바일/웹 클라이언트 확장" 항목을 [결정/구현]로 갱신.
   - 웹 스모크 체크리스트를 `docs/dev/웹 테스트 가이드.md`로 신설(2단계 체크리스트 정착).
5. 커밋: `feat: 웹 로딩 화면과 미지원 브라우저 안내 추가`, `docs: KMP 가이드에 웹(wasmJs) 지침 추가` 등.

### 6단계(배포 준비, 선택) — 테스터 공개

테스터 공개에는 서버(+PostgreSQL)의 공개 배포가 전제된다. **이 계획의 범위는 "bootJar 하나로 웹+API가 서빙되는 상태"까지**이며, 실제 호스팅(클라우드 선택, HTTPS, 도메인, 운영 DB)은 별도 결정 사항이다. 구현 세션은 이 단계에 도달하면 호스팅 옵션(트레이드오프 포함)을 정리해 오너에게 제시할 것. 주의: 운영 배포 전 체크리스트(메모리 session-handoff의 concept_id 드롭·백필 SQL, ExtraStudyItem @Embedded nullable 재확인, JWT 시크릿·admin 부트스트랩 환경변수)와 함께 다뤄야 한다.

## 6. 웹 전용 설계 결정 상세 (구현 시 그대로 따를 것)

| 결정 | 내용 | 근거 |
|---|---|---|
| `platformApiBaseUrl()` wasmJs | `window.location.origin` | 레포지토리가 `"$baseUrl/api/..."` 연결(§3.1), `App.kt:518`이 `Url(...).host`로 파싱 → 절대 URL 필요. 같은 오리진 서빙이므로 origin이 곧 API 서버. dev server에서는 프록시가 같은 오리진을 유지 |
| `SystemBackHandler` wasmJs | no-op | iOS/JVM과 동일한 기존 트레이드오프. 브라우저 뒤로가기는 앱 백스택과 무관(탭 이탈) — popstate 연동은 백로그 |
| `Settings()` wasmJs | no-arg 그대로 → localStorage(StorageSettings) | 코드 무변경으로 토큰·온보딩·테마 영속화. 오너 결정 3 |
| `@Preview`(App.kt) | 조치 불필요 | Android SDK가 아니라 JetBrains 멀티플랫폼 아티팩트 — iOS 타깃이 이미 포함 빌드 중 |
| 시간 처리 | 조치 불필요 | `ScrapTimeFormatter`가 kotlin.time + 순수 정수 달력 변환("JVM·Android·Wasm 동일 동작" 주석 명시) |
| URL 라우팅 | 도입하지 않음 | 앱은 단일 캔버스 페이지(딥링크 요구 없음, 전 플랫폼 동일). SPA fallback 컨트롤러도 불필요 |
| CORS | 추가하지 않음 | 같은 오리진 서빙(오너 결정 1). 별도 호스팅 전환 시에만 필요(백로그) |

## 7. 리스크와 검증 게이트

| # | 리스크 | 심각도 | 대응 |
|---|---|---|---|
| 1 | **한글 IME 조합 입력**(canvas 기반 텍스트 입력) 결함 — 주관식 한글 답변이 핵심 UX | **최상** | 1단계 게이트로 최우선 검증. 결함 시 중단 후 오너 보고(§5-1단계) |
| 2 | 신규 AGP KMP 플러그인(`androidLibrary`)과 wasmJs 타깃 공존 문제 | 중 | 1단계에서 android/iOS/jvm 전 타깃 빌드+테스트 green으로 즉시 검증 |
| 2b | material3가 CMP와 별개 버전 `1.11.0-alpha07`(alpha) — wasmJs variant 해소 실패 가능성 | 중 | 1단계 `compileKotlinWasmJs`에서 material3 wasmJs variant 해소를 명시 확인. 실패 시 material3 버전 조정을 오너에게 보고 |
| 3 | multiplatform-settings 1.3.0 no-arg의 wasmJs 매핑 | 중 | 문서상 지원(localStorage). 2단계 새로고침 스모크로 실검증. 실패 시 `StorageSettings(localStorage)` 명시 생성으로 대체(expect/actual 1개 추가) |
| 4 | `.wasm` MIME 미매핑으로 스트리밍 컴파일 실패/경고 | 하 | 3단계에서 Content-Type 확인, 필요 시 MimeMappings 추가 |
| 5 | 번들 크기(수 MB)로 인한 첫 로드 지연 | 중 | 5단계 로딩 화면. 프로덕션 번들(minify) 사용. 추가 최적화(캐싱 헤더)는 배포 단계에서 |
| 6 | 웹 접근성 — CMP 웹의 스크린리더 지원은 아직 제한적("낭독" 시맨틱의 웹 실효성) | 중 | 기능 저하 없음(시맨틱은 컴파일·동작엔 무해). 웹 접근성 실효성은 알려진 CMP 제약으로 문서화하고 CMP 버전업 추적(백로그) |
| 7 | 서버 빌드가 웹 빌드에 결합되어 서버 개발 루프 저하 | 중 | 3단계에서 게이트 방식 비교·택일(§5-3단계 1) — `:server:test`는 웹 빌드 없이 돌도록 유지 |
| 8 | 세션당 상태가 서버에 있으므로 웹·모바일 동시 사용 시 같은 계정 충돌 | 하 | 이미 서버가 단일 진실(무상태 클라). 낙관적 UI 없음 — 추가 조치 불필요. QA에서 교차 기기 시나리오 1회 확인 |

**공통 검증 게이트(모든 커밋)**: `:server:test` + `:app:shared:jvmTest` + `:app:shared:compileKotlinWasmJs`(+ 관련 타깃 컴파일) 실패 0. 최종 단계에서 verifier 패스(OMC 관례) 수행.

## 8. 명시적 비범위 (백로그)

- 브라우저 뒤로가기(popstate)↔앱 백스택 연동, URL 라우팅/딥링크/공유 URL
- Kotlin/JS 폴백 타깃, 구형 브라우저 지원
- httpOnly 쿠키 인증 전환 등 웹 토큰 보안 강화
- PWA/오프라인, 웹 푸시, SEO(캔버스 렌더링 특성상 비대상)
- 문제 풀이 화면 힌트 사이드 패널(§5 4-2), Material3 window-size-class 아티팩트 전환
- 별도 정적 호스팅 + CORS 구성
- 실서비스 호스팅/도메인/HTTPS(§5 6단계에서 별도 결정)

## 9. 구현 세션 착수 체크리스트

1. 이 문서 + `CLAUDE.md` + `docs/dev/KMP 개발 가이드.md` + `docs/ux/UX 핵심 가이드.md` 숙지.
2. §3.3 잔여물 삭제 → 1단계부터 순서대로. 단계 내 검증 게이트를 통과해야 다음 단계로.
3. 막히면(특히 IME 게이트, CMP 버전 이슈) 추측 진행 금지 — 오너 반문.

**핵심 참고 파일(전부 실존 확인됨):**
- `settings.gradle.kts`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `app/shared/build.gradle.kts`
- `app/shared/src/commonMain/kotlin/watson/bytecs/App.kt` (엔트리 컴포저블·의존성 조립·백스택·화면 목록)
- `app/shared/src/commonMain/kotlin/watson/bytecs/problem/data/HttpClientFactory.kt` (expect 2개)
- `app/shared/src/commonMain/kotlin/watson/bytecs/SystemBackHandler.kt`, `.../Platform.kt` (expect)
- `app/shared/src/androidMain/kotlin/watson/bytecs/problem/data/HttpClientFactory.android.kt` (actual 작성 패턴 참고)
- `app/shared/src/iosMain/kotlin/watson/bytecs/MainViewController.kt` (엔트리포인트 대응물 패턴)
- `app/shared/src/commonMain/kotlin/watson/bytecs/account/data/AuthHttpClientFactory.kt`, `.../TokenStore.kt`
- `app/shared/src/commonMain/kotlin/watson/bytecs/ui/theme/Dimens.kt` (`contentMax` 등 반응형 치수)
- `server/src/main/kotlin/watson/bytecs/config/SecurityConfig.kt`, `server/build.gradle.kts`
