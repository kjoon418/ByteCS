package watson.bytecs

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import watson.bytecs.account.AccountRepository
import watson.bytecs.account.AccountScreen
import watson.bytecs.account.AccountViewModel
import watson.bytecs.account.AuthMode
import watson.bytecs.account.AuthState
import watson.bytecs.account.AuthViewModel
import watson.bytecs.account.LoginScreen
import watson.bytecs.account.SessionManager
import watson.bytecs.account.data.KtorAccountRepository
import watson.bytecs.account.data.TokenStore
import watson.bytecs.account.data.createAuthenticatedHttpClient
import watson.bytecs.account.data.createTokenStore
import watson.bytecs.categoryhistory.CategoryHistoryDetailScreen
import watson.bytecs.categoryhistory.CategoryHistoryDetailViewModel
import watson.bytecs.categoryhistory.CategoryHistoryListScreen
import watson.bytecs.categoryhistory.CategoryHistoryListViewModel
import watson.bytecs.categoryhistory.CategoryHistoryProblemDetailScreen
import watson.bytecs.categoryhistory.CategoryHistoryProblemDetailViewModel
import watson.bytecs.categoryhistory.CategoryHistoryRepository
import watson.bytecs.categoryhistory.data.KtorCategoryHistoryRepository
import io.ktor.http.Url
import watson.bytecs.problem.data.platformApiBaseUrl
import watson.bytecs.session.CompletionSummary
import watson.bytecs.session.HomeScreen
import watson.bytecs.session.HomeViewModel
import watson.bytecs.session.SessionCompleteScreen
import watson.bytecs.session.SessionRepository
import watson.bytecs.session.SessionScreen
import watson.bytecs.session.SessionViewModel
import watson.bytecs.onboarding.OnboardingScreen
import watson.bytecs.onboarding.OnboardingStore
import watson.bytecs.onboarding.createOnboardingStore
import watson.bytecs.report.ContentReportRepository
import watson.bytecs.report.ReportCategory
import watson.bytecs.report.ReportScreen
import watson.bytecs.report.ReportViewModel
import watson.bytecs.report.data.KtorContentReportRepository
import watson.bytecs.scrap.ScrapDetailScreen
import watson.bytecs.scrap.ScrapDetailViewModel
import watson.bytecs.scrap.ScrapListScreen
import watson.bytecs.scrap.ScrapListViewModel
import watson.bytecs.scrap.ScrapRepository
import watson.bytecs.scrap.data.KtorScrapRepository
import watson.bytecs.session.data.KtorSessionRepository
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.BcsTheme
import watson.bytecs.ui.theme.LocalBcsColors
import watson.bytecs.ui.theme.ThemeController
import watson.bytecs.ui.theme.ThemeMode
import watson.bytecs.ui.theme.createThemeController

/**
 * 앱 루트. 앱 수명 동안 유지되는 싱글턴(토큰 저장소·인증 HTTP 클라이언트·저장소·세션·테마)을 한 번만 만들고,
 * 부팅 시 인증 상태를 복원한 뒤 [Screen] 상태와 명시적 백스택으로 화면을 그린다.
 *
 * 프리뷰·테스트는 [dependencies]에 Fake 저장소를 주입해 백엔드 없이 구동할 수 있다.
 */
@Composable
@Preview
fun App(
    dependencies: AppDependencies = rememberDefaultAppDependencies(),
) {
    // 컴포지션 이탈 시 공유 HTTP 클라이언트를 정리한다.
    DisposableEffect(dependencies) {
        onDispose { dependencies.close() }
    }

    // 앱 시작 시 인증 상태 복원(토큰 없으면 게스트 발급, 있으면 신원 확인).
    LaunchedEffect(dependencies) {
        dependencies.sessionManager.bootstrap()
    }

    val themeMode by dependencies.themeController.mode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    BcsTheme(darkTheme = darkTheme) {
        AppNavHost(dependencies)
    }
}

/**
 * 상태 기반 내비게이션(내비 라이브러리 없음). 명시적 백스택으로 이동·뒤로가기를 지원한다.
 * 게스트 발급이 실패(오프라인 등)해 [AuthState.BootstrapFailed]면 화면 대신 재시도 안내를 그려
 * 막다른 길(영구 로딩)을 만들지 않는다.
 *
 * ⭐️ 부트스트랩이 끝나기 전([AuthState.Loading])에는 홈을 그리지 않는다: 게스트 토큰이 저장되기 전에
 * 홈이 `getToday()`를 호출하면 401이 나서 "오늘의 한입을 불러오지 못했어요"로 떨어진다(첫 실행 레이스).
 * 어느 국면을 그릴지는 순수 함수 [rootPhase]가 결정한다(가드 테스트로 고정).
 */
@Composable
private fun AppNavHost(dependencies: AppDependencies) {
    val authState by dependencies.sessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 온보딩 "봤음"은 로컬 영속 플래그다. 완료 시 즉시 true로 올려 다음 국면(Main)으로 넘어간다.
    var onboardingSeen by remember { mutableStateOf(dependencies.onboardingStore.hasSeenOnboarding()) }
    // 온보딩에서 로그인으로 진입하면, Main으로 넘어갈 때 백스택 위에 로그인을 얹는다.
    var pendingAfterOnboarding by remember { mutableStateOf<Screen?>(null) }

    fun completeOnboarding(next: Screen? = null) {
        dependencies.onboardingStore.markOnboardingSeen()
        pendingAfterOnboarding = next
        onboardingSeen = true
    }

    when (rootPhase(authState, onboardingSeen)) {
        RootPhase.BootstrapFailed -> {
            BootstrapErrorScreen(onRetry = { scope.launch { dependencies.sessionManager.retry() } })
            return
        }
        // 토큰 저장 전 홈 진입을 막는 안전판(첫 실행 레이스 수정).
        RootPhase.Loading -> {
            BootstrapLoadingScreen()
            return
        }
        // 최초 실행 1회 — 01 온보딩. 부트스트랩이 끝난 뒤라 "바로 시작"에 토큰이 준비돼 있다.
        RootPhase.Onboarding -> {
            OnboardingScreen(
                onStart = { completeOnboarding() },
                onLogin = { completeOnboarding(Screen.Login(AuthMode.Login)) },
            )
            return
        }
        RootPhase.Main -> Unit
    }

    // 항상 홈을 밑바닥에 둔다(재방문 기본 진입점·막다른 길 방지).
    // 온보딩에서 로그인을 택했으면 그 목적지를 백스택 위에 한 번만 얹는다.
    val backStack = remember {
        mutableStateListOf<Screen>(Screen.Home).also { stack ->
            pendingAfterOnboarding?.let { stack.add(it) }
        }
    }
    val current = backStack.last()

    fun navigate(screen: Screen) = backStack.pushDistinct(screen)
    fun back() = backStack.popScreen()
    fun resetTo(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }

    // ⭐️ [실기기 QA] 안드로이드 하드웨어/제스처 뒤로가기를 앱의 명시적 백스택에 연결한다. 홈만 남았을
    //    때(size==1)는 비활성화해 시스템 기본 동작(앱 종료)에 위임한다. BackHandler가 없으면 커스텀
    //    백스택과 무관하게 액티비티가 곧장 종료돼, 여러 화면을 쌓아둔 상태에서도 뒤로가기 한 번에 앱이
    //    닫혔다 — 사용자는 이전 화면으로의 이동을 기대한다.
    SystemBackHandler(enabled = backStack.size > 1) { back() }

    when (val screen = current) {
        Screen.Home -> {
            val viewModel = viewModel { HomeViewModel(dependencies.sessionRepository, dependencies.sessionManager) }
            HomeScreen(
                viewModel = viewModel,
                onStartOrContinue = { navigate(Screen.Session()) },
                // '조금 더 풀기'(D6·D9 일원화 — 추가 학습 폐지) — 새 세션으로 같은 풀이 흐름에 재진입한다.
                onExtraPractice = { navigate(Screen.Session(startNext = true)) },
                onOpenAccount = { navigate(Screen.Account) },
                onUpgrade = { navigate(Screen.Login(AuthMode.Register)) },
                onOpenScrapList = { navigate(Screen.ScrapList) },
                onOpenCategoryHistory = { navigate(Screen.CategoryHistoryList) },
            )
        }

        is Screen.Session -> {
            val viewModel = viewModel { SessionViewModel(dependencies.sessionRepository) }
            SessionScreen(
                viewModel = viewModel,
                startNext = screen.startNext,
                onCompleted = { summary ->
                    // 세션을 백스택에서 걷어내고 완료 화면으로(완료→뒤로가면 홈).
                    back()
                    navigate(Screen.SessionComplete(summary))
                },
                onExit = { back() },
                onReport = { problemId, presetCategory -> navigate(Screen.Report(problemId, presetCategory)) },
                scrapRepository = dependencies.scrapRepository,
            )
        }

        is Screen.SessionComplete -> {
            SessionCompleteScreen(
                summary = screen.summary,
                onDone = { back() },
                // '조금 더 풀기'(D6·D9 일원화) — 완료 화면을 먼저 걷어낸 뒤 새 세션으로 재진입한다.
                // ⭐️ [실기기 QA] 걷어내지 않으면 완료→조금 더 풀기→완료를 반복할 때마다 완료 화면이 백스택에
                //    누적돼, '오늘은 여기까지'·뒤로가기 한 번으로 홈에 닿지 못하고 이전 완료 화면이 다시 보인다.
                //    완료 화면은 항상 홈 바로 위 하나만 유지된다(back → 완료 pop → 세션 push).
                onMore = {
                    back()
                    navigate(Screen.Session(startNext = true))
                },
            )
        }

        Screen.Account -> {
            val viewModel = viewModel {
                AccountViewModel(dependencies.sessionManager, dependencies.themeController)
            }
            AccountScreen(
                viewModel = viewModel,
                // 게스트 가입 CTA는 가입 모드로 진입해 승계 배너를 바로 보여준다.
                onNavigateToLogin = { navigate(Screen.Login(AuthMode.Register)) },
                // 온보딩(01)은 이 슬라이스 범위 밖 → 홈으로 라우팅한다(TODO: 온보딩 연결).
                onDeleted = { resetTo(Screen.Home) },
                onBack = { back() },
                onOpenScrapList = { navigate(Screen.ScrapList) },
            )
        }

        is Screen.Login -> {
            val viewModel = viewModel { AuthViewModel(dependencies.sessionManager, screen.initialMode) }
            LoginScreen(
                viewModel = viewModel,
                initialMode = screen.initialMode,
                onSuccess = { back() },
                onBack = { back() },
            )
        }

        is Screen.Report -> {
            // ⭐️ key로 대상 문제를 구분하지 않으면, 한 번 만든 ReportViewModel이 클래스 단위로 캐시돼(전역
            //    ViewModelStore) 다른 문제를 신고할 때도 첫 problemId·presetCategory 인스턴스가 재사용된다.
            val viewModel = viewModel(key = detailViewModelKey(screen)) {
                ReportViewModel(dependencies.contentReportRepository, screen.problemId, screen.presetCategory)
            }
            ReportScreen(
                viewModel = viewModel,
                onClose = { back() },
            )
        }

        Screen.ScrapList -> {
            val viewModel = viewModel { ScrapListViewModel(dependencies.scrapRepository) }
            ScrapListScreen(
                viewModel = viewModel,
                onOpenScrap = { problemId -> navigate(Screen.ScrapDetail(problemId)) },
                onBack = { back() },
            )
        }

        is Screen.ScrapDetail -> {
            // ⭐️ key로 문제를 구분하지 않으면 첫 재열람에서 만든 뷰모델이 재사용돼, 다른 스크랩을 열어도
            //    처음 본 문제가 그대로 뜬다(전역 ViewModelStore·클래스 단위 캐시).
            val viewModel = viewModel(key = detailViewModelKey(screen)) {
                ScrapDetailViewModel(dependencies.scrapRepository, screen.problemId)
            }
            ScrapDetailScreen(
                viewModel = viewModel,
                onReport = { problemId, presetCategory -> navigate(Screen.Report(problemId, presetCategory)) },
                onBack = { back() },
            )
        }

        Screen.CategoryHistoryList -> {
            val viewModel = viewModel { CategoryHistoryListViewModel(dependencies.categoryHistoryRepository) }
            CategoryHistoryListScreen(
                viewModel = viewModel,
                onOpenCategory = { category -> navigate(Screen.CategoryHistoryDetail(category)) },
                onBack = { back() },
            )
        }

        is Screen.CategoryHistoryDetail -> {
            // ⭐️ [실기기 QA] key로 카테고리를 구분하지 않으면, 처음 연 카테고리의 뷰모델이 클래스 단위로
            //    캐시돼(내비 라이브러리가 없어 ViewModelStore가 앱 전역) 이후 어떤 카테고리를 눌러도 첫
            //    카테고리 문제만 보인다. category별 key로 인스턴스를 분리한다.
            val viewModel = viewModel(key = detailViewModelKey(screen)) {
                CategoryHistoryDetailViewModel(dependencies.categoryHistoryRepository, screen.category)
            }
            CategoryHistoryDetailScreen(
                viewModel = viewModel,
                category = screen.category,
                onOpenProblem = { problemId ->
                    navigate(Screen.CategoryHistoryProblemDetail(screen.category, problemId))
                },
                onBack = { back() },
            )
        }

        is Screen.CategoryHistoryProblemDetail -> {
            val viewModel = viewModel(key = detailViewModelKey(screen)) {
                CategoryHistoryProblemDetailViewModel(dependencies.categoryHistoryRepository, screen.category, screen.problemId)
            }
            CategoryHistoryProblemDetailScreen(viewModel = viewModel, onBack = { back() })
        }
    }
}

/**
 * 부트스트랩(게스트 발급·신원 확인) 진행 중 국면. 홈을 그리지 않고 은은한 로딩만 둔다.
 * ⭐️ 이 화면이 있어야 토큰 저장 전 `getToday()` 호출(첫 실행 레이스)이 원천 차단된다.
 */
@Composable
private fun BootstrapLoadingScreen() {
    BcsScaffold {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = BcsDimens.space5),
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(BcsDimens.emptyStateIcon),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = BcsDimens.loaderStroke,
            )
            Text(
                text = "준비하고 있어요…",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalBcsColors.current.textSecondary,
            )
        }
    }
}

/** §5.12 부팅 실패(게스트 발급 불가) — 막다른 길 금지. 자산 안전 고지 + 재시도 경로. */
@Composable
private fun BootstrapErrorScreen(onRetry: () -> Unit) {
    val colors = LocalBcsColors.current
    BcsScaffold {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = BcsDimens.space5),
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "연결을 준비하지 못했어요",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = "인터넷 연결을 확인하고 다시 시도해 주세요. 학습 기록은 안전해요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            PrimaryButton(text = "다시 시도하기", onClick = onRetry)
        }
    }
}

/**
 * 앱 루트가 그릴 최상위 국면. [rootPhase]가 [AuthState]에서 파생한다.
 * 국면 분기를 순수 함수로 빼 둔 이유: "Loading이면 홈을 그리지 않는다"는 첫 실행 레이스 가드를
 * Compose 트리 밖에서 결정적으로 테스트하기 위함(인수인계 §5.2 — 가드는 순수 함수로).
 */
internal enum class RootPhase {
    /** 부트스트랩 진행 중 — 로딩만 그린다(홈 진입 차단). */
    Loading,

    /** 게스트 발급 실패 — 재시도 안내. */
    BootstrapFailed,

    /** 최초 실행 1회 — 01 온보딩 시작 화면. */
    Onboarding,

    /** 인증 확정 + 온보딩 완료 — 내비게이션 진입. */
    Main,
}

/**
 * ([AuthState], 온보딩 노출 여부) → [RootPhase].
 *  - ⚠️ [AuthState.Loading]은 반드시 [RootPhase.Loading]으로 가야 한다
 *    (Main으로 새면 토큰 저장 전 홈이 `getToday()`를 호출해 첫 실행 레이스가 재발한다).
 *  - 인증이 확정된 뒤, 온보딩을 아직 안 봤으면([onboardingSeen]=false) 01을 먼저 그린다.
 *    부트스트랩(게스트 발급)이 끝난 뒤라 온보딩에서 "바로 시작"하면 토큰이 이미 준비돼 있다.
 */
internal fun rootPhase(authState: AuthState, onboardingSeen: Boolean): RootPhase = when {
    authState is AuthState.BootstrapFailed -> RootPhase.BootstrapFailed
    authState is AuthState.Loading -> RootPhase.Loading
    !onboardingSeen -> RootPhase.Onboarding
    else -> RootPhase.Main
}

/** 화면 목적지. */
sealed interface Screen {
    /** 02 홈('오늘의 한입') — 기본 진입점. */
    data object Home : Screen

    /**
     * 03 세션 풀이. [startNext]가 true면 '조금 더 풀기'(D6·D9 일원화 — 추가 학습 폐지)로 진입한 것이라,
     * 오늘 최신 세션이 완료 상태일 때 새 세션을 만들어(`POST /today/next`) 같은 풀이 흐름에 재진입한다.
     * 기본(false)은 기존 시작·이어서(`GET /today`)와 동일하다.
     */
    data class Session(val startNext: Boolean = false) : Screen

    data object Account : Screen

    /** 04 세션 완료 — 완료 요약을 실어 넘긴다. */
    data class SessionComplete(val summary: CompletionSummary) : Screen

    /** 로그인·가입. 진입 모드([initialMode])로 가입/로그인 중 무엇을 먼저 보일지 정한다. */
    data class Login(val initialMode: AuthMode) : Screen

    /**
     * 07 콘텐츠 오류 신고. 신고 대상 문제([problemId])를 실어 넘긴다.
     * [presetCategory](D2)가 있으면 신고 화면이 그 유형을 미리 선택해 진입한다 — 정답 공개 패널의
     * '내 답이 맞았던 것 같아요'가 [watson.bytecs.report.ReportCategory.WRONG_ANSWER]를 프리셋으로 넘긴다.
     * 일반 '오류 신고' 진입점은 null(프리셋 없음, 사용자가 직접 유형을 고른다).
     */
    data class Report(val problemId: Long, val presetCategory: ReportCategory? = null) : Screen

    /** 스크랩 목록(기능 5). */
    data object ScrapList : Screen

    /** 스크랩 재열람(읽기 전용). 재열람할 문제([problemId])를 실어 넘긴다. */
    data class ScrapDetail(val problemId: Long) : Screen

    /** 카테고리별 학습 이력 목록(기능 7, 1차) — 8개 카테고리와 각 문제 수. */
    data object CategoryHistoryList : Screen

    /** 카테고리별 학습 이력 상세(읽기 전용). 선택한 카테고리([category], 서버 enum name)를 실어 넘긴다. */
    data class CategoryHistoryDetail(val category: String) : Screen

    /** 카테고리 이력의 한 문제 상세(읽기 전용, 레벨3). 카테고리([category])와 문제([problemId])를 실어 넘긴다. */
    data class CategoryHistoryProblemDetail(val category: String, val problemId: Long) : Screen
}

/**
 * 명시적 백스택(내비 라이브러리 없음) 조작을 순수 함수로 빼 둔다 — '조금 더 풀기' 재진입 시 완료 화면이
 * 누적되지 않는다는 불변식을 Compose 트리 밖에서 결정적으로 테스트하기 위함([rootPhase] 가드와 같은 결).
 */
internal fun MutableList<Screen>.pushDistinct(screen: Screen) {
    // 같은 화면 중복 push 방지.
    if (lastOrNull() != screen) add(screen)
}

/** 맨 위 화면을 pop. 홈(밑바닥)은 남긴다 — 막다른 길 방지. */
internal fun MutableList<Screen>.popScreen() {
    if (size > 1) removeAt(lastIndex)
}

/**
 * 상세 화면 뷰모델의 캐시 [viewModel] key. 내비 라이브러리가 없어 ViewModelStore가 앱 전역이라, 같은 화면
 * 종류의 서로 다른 대상(문제·카테고리)이 이전 인스턴스를 재사용하지 않도록 **대상별로 유일한** key가 필요하다
 * (실기기 QA — key가 없으면 처음 연 항목이 그대로 고정됐다). 화면 종류마다 접두사를 달리해 종류 간 충돌도 막는다.
 * key가 필요 없는 화면(단일 인스턴스)은 null을 돌려 클래스 단위 기본 캐시를 쓴다.
 *
 * 순수 함수로 빼 둔 이유: "서로 다른 대상 → 서로 다른 key"라는 불변식을 Compose 트리 밖에서 못박기 위함
 * (원래 버그가 컴파일되고 모든 화면 테스트를 통과하던 유형이라, 회귀를 이 단위 테스트로 잡는다).
 */
internal fun detailViewModelKey(screen: Screen): String? = when (screen) {
    is Screen.Report -> "report:${screen.problemId}:${screen.presetCategory}"
    is Screen.ScrapDetail -> "scrap-detail:${screen.problemId}"
    is Screen.CategoryHistoryDetail -> "category-history-detail:${screen.category}"
    is Screen.CategoryHistoryProblemDetail ->
        "category-history-problem:${screen.category}:${screen.problemId}"
    else -> null
}

/**
 * 앱 전역 의존성 묶음. 앱 수명 동안 한 번만 생성되며, [close]로 보유 자원(HTTP 클라이언트)을 정리한다.
 * 프리뷰·테스트는 Fake 저장소로 직접 구성할 수 있다.
 */
class AppDependencies(
    val accountRepository: AccountRepository,
    val sessionRepository: SessionRepository,
    val contentReportRepository: ContentReportRepository,
    val scrapRepository: ScrapRepository,
    val categoryHistoryRepository: CategoryHistoryRepository,
    val sessionManager: SessionManager,
    val themeController: ThemeController,
    val tokenStore: TokenStore,
    val onboardingStore: OnboardingStore,
    private val onClose: () -> Unit = {},
) {
    fun close() = onClose()
}

/**
 * 실제 백엔드에 붙는 기본 의존성. 문제·계정 요청이 **하나의 인증 클라이언트**를 공유해,
 * 저장된 토큰이 있으면 자동으로 `Authorization: Bearer`가 붙는다(게스트→회원 교체도 재생성 없이 반영).
 * 클라이언트 수명은 여기(App)가 단독으로 소유·종료한다 — 저장소는 주입된 공유 클라이언트를 닫지 않는다.
 */
@Composable
private fun rememberDefaultAppDependencies(): AppDependencies = remember {
    val tokenStore = createTokenStore()
    // ⭐️ 전송 시점마다 최신 토큰을 읽는다 — 게스트 발급/로그인으로 토큰이 바뀌어도 클라이언트 재생성 불필요.
    // 토큰은 API 호스트로 가는 요청에만 붙인다(다른 호스트로의 유출 차단).
    val client = createAuthenticatedHttpClient(
        tokenProvider = { tokenStore.get() },
        apiHost = Url(platformApiBaseUrl()).host,
    )
    val accountRepository = KtorAccountRepository(client)
    val sessionRepository = KtorSessionRepository(client)
    val contentReportRepository = KtorContentReportRepository(client)
    val scrapRepository = KtorScrapRepository(client)
    val categoryHistoryRepository = KtorCategoryHistoryRepository(client)
    val sessionManager = SessionManager(accountRepository, tokenStore)
    AppDependencies(
        accountRepository = accountRepository,
        sessionRepository = sessionRepository,
        contentReportRepository = contentReportRepository,
        scrapRepository = scrapRepository,
        categoryHistoryRepository = categoryHistoryRepository,
        sessionManager = sessionManager,
        themeController = createThemeController(),
        tokenStore = tokenStore,
        onboardingStore = createOnboardingStore(),
        // 공유 클라이언트의 단일 소유자로서 한 번만 닫는다.
        onClose = { client.close() },
    )
}
