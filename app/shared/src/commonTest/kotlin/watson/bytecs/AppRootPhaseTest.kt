package watson.bytecs

import watson.bytecs.account.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 첫 실행 레이스 가드(인수인계 §4)와 온보딩 게이팅. [rootPhase]가 국면을 옳게 파생하는지 순수 함수로 못박는다.
 *
 * ⭐️ 핵심 불변식: [AuthState.Loading]은 절대 [RootPhase.Main]으로 흐르면 안 된다.
 * Main으로 새면 게스트 토큰 저장 전 홈이 `getToday()`를 호출해 401 → "불러오지 못했어요"가 재발한다.
 */
class AppRootPhaseTest {

    // ── 첫 실행 레이스 가드 ────────────────────────────────────────────────────

    /** 이 한 줄이 레이스 수정의 본체다: Loading이 Main으로 새는 순간 첫 실행이 깨진다. */
    @Test
    fun loading은_온보딩_여부와_무관하게_Loading_국면이다() {
        assertEquals(RootPhase.Loading, rootPhase(AuthState.Loading, onboardingSeen = true))
        // ⭐️ 온보딩을 아직 안 봤어도, 부트스트랩 중에는 온보딩이 아니라 로딩이 먼저다(토큰 준비 우선).
        assertEquals(RootPhase.Loading, rootPhase(AuthState.Loading, onboardingSeen = false))
    }

    @Test
    fun 부트스트랩_실패는_온보딩_여부와_무관하게_BootstrapFailed_국면이다() {
        assertEquals(RootPhase.BootstrapFailed, rootPhase(AuthState.BootstrapFailed, onboardingSeen = true))
        assertEquals(RootPhase.BootstrapFailed, rootPhase(AuthState.BootstrapFailed, onboardingSeen = false))
    }

    // ── 온보딩 게이팅(최초 실행 1회) ───────────────────────────────────────────

    /** 인증이 확정됐고 온보딩을 안 봤으면 01을 먼저 그린다. */
    @Test
    fun 인증_확정_후_온보딩을_안_봤으면_Onboarding_국면이다() {
        assertEquals(RootPhase.Onboarding, rootPhase(AuthState.Guest(account = null), onboardingSeen = false))
        assertEquals(RootPhase.Onboarding, rootPhase(AuthState.Member(account = null), onboardingSeen = false))
    }

    /** 온보딩을 이미 봤으면 다시 노출하지 않고 Main으로 간다(1회성). */
    @Test
    fun 온보딩을_이미_봤으면_Main_국면이다() {
        assertEquals(RootPhase.Main, rootPhase(AuthState.Guest(account = null), onboardingSeen = true))
        assertEquals(RootPhase.Main, rootPhase(AuthState.Member(account = null), onboardingSeen = true))
    }
}
