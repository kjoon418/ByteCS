package watson.bytecs.onboarding

import com.russhwolf.settings.Settings

/**
 * 온보딩(01 시작 화면) 노출 여부의 영속 저장소. "봤음"을 기기에 남겨 최초 실행 1회만 노출한다.
 *
 * ⭐️ 온보딩은 프론트 전용이다 — 서버 왕복이나 계정 상태와 무관하게, 이 로컬 플래그 하나로 노출을 판단한다.
 * 구현을 교체 가능하게 두어(테스트는 [com.russhwolf.settings.MapSettings]) 실기기 스토리지 없이 검증한다.
 * (토큰 영속 [watson.bytecs.account.data.TokenStore]·테마 [watson.bytecs.ui.theme.ThemeController]와 같은 관례.)
 */
interface OnboardingStore {
    /** 온보딩을 이미 봤으면 true(=다시 안 보여준다). */
    fun hasSeenOnboarding(): Boolean

    /** 온보딩을 봤다고 표시한다(시작하기·로그인 진입 시). */
    fun markOnboardingSeen()
}

/**
 * multiplatform-settings 기반 [OnboardingStore]. Android=SharedPreferences, iOS=NSUserDefaults에
 * 플랫폼 코드 없이 저장한다.
 */
class SettingsOnboardingStore(
    private val settings: Settings,
) : OnboardingStore {

    override fun hasSeenOnboarding(): Boolean = settings.getBoolean(KEY, false)

    override fun markOnboardingSeen() {
        settings.putBoolean(KEY, true)
    }

    private companion object {
        const val KEY = "onboarding_seen"
    }
}

/**
 * 앱이 쓰는 기본 [OnboardingStore]. no-arg `Settings()`가 플랫폼 저장소를 자동 초기화한다.
 */
fun createOnboardingStore(): OnboardingStore = SettingsOnboardingStore(Settings())
