package watson.bytecs.onboarding

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SettingsOnboardingStore가 "봤음" 플래그를 영속하는지 MapSettings(인메모리)로 검증한다.
 * 이 플래그가 최초 실행 1회 노출의 단일 판단 근거다.
 */
class OnboardingStoreTest {

    @Test
    fun 저장_전에는_아직_안_본_상태다() {
        val store = SettingsOnboardingStore(MapSettings())
        assertFalse(store.hasSeenOnboarding(), "최초 실행에는 온보딩을 노출해야 한다")
    }

    @Test
    fun 봤다고_표시하면_이후에는_안_본_상태로_돌아가지_않는다() {
        val store = SettingsOnboardingStore(MapSettings())
        store.markOnboardingSeen()
        assertTrue(store.hasSeenOnboarding(), "한 번 보면 다시 노출하지 않는다")
    }

    @Test
    fun 같은_저장소를_재생성해도_봤음이_유지된다() {
        // 앱 재실행을 흉내: 같은 Settings 백엔드를 공유하는 새 인스턴스.
        val settings = MapSettings()
        SettingsOnboardingStore(settings).markOnboardingSeen()

        val reopened = SettingsOnboardingStore(settings)
        assertTrue(reopened.hasSeenOnboarding(), "재실행 후에도 온보딩을 다시 띄우지 않는다")
    }
}
