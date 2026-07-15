package watson.bytecs

import watson.bytecs.account.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 첫 실행 레이스 가드(인수인계 §4). [rootPhase]가 국면을 옳게 파생하는지 순수 함수로 못박는다.
 *
 * ⭐️ 핵심 불변식: [AuthState.Loading]은 절대 [RootPhase.Main]으로 흐르면 안 된다.
 * Main으로 새면 게스트 토큰 저장 전 홈이 `getToday()`를 호출해 401 → "불러오지 못했어요"가 재발한다.
 */
class AppRootPhaseTest {

    @Test
    fun loading은_Main이_아니라_Loading_국면이다() {
        // 이 한 줄이 레이스 수정의 본체다: Loading이 Main으로 새는 순간 첫 실행이 깨진다.
        assertEquals(RootPhase.Loading, rootPhase(AuthState.Loading))
    }

    @Test
    fun 부트스트랩_실패는_BootstrapFailed_국면이다() {
        assertEquals(RootPhase.BootstrapFailed, rootPhase(AuthState.BootstrapFailed))
    }

    @Test
    fun 게스트로_확정되면_Main_국면이다() {
        assertEquals(RootPhase.Main, rootPhase(AuthState.Guest(account = null)))
    }

    @Test
    fun 회원으로_확정되면_Main_국면이다() {
        assertEquals(RootPhase.Main, rootPhase(AuthState.Member(account = null)))
    }
}
