package watson.bytecs.account.data

import com.russhwolf.settings.Settings

/**
 * 인증 토큰의 영속 저장소. 기기에 토큰을 보관해 앱 재실행 뒤에도 같은(게스트/회원) 신원을 잇는다.
 *
 * 구현을 교체 가능하게 두어(테스트는 [com.russhwolf.settings.MapSettings] 주입) 실기기 스토리지 없이도
 * 결정적으로 검증한다.
 */
interface TokenStore {
    /** 저장된 토큰. 없으면 null. */
    fun get(): String?

    /** 토큰을 저장한다(게스트 발급·로그인·가입 승격 시). */
    fun set(token: String)

    /** 토큰을 지운다(로그아웃·계정 삭제·만료 토큰 정리 시). */
    fun clear()
}

/**
 * multiplatform-settings 기반 [TokenStore]. Android=SharedPreferences, iOS=NSUserDefaults에
 * 플랫폼 코드 없이 저장한다.
 */
class SettingsTokenStore(
    private val settings: Settings,
) : TokenStore {

    override fun get(): String? = settings.getStringOrNull(KEY)

    override fun set(token: String) {
        settings.putString(KEY, token)
    }

    override fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "auth_token"
    }
}

/**
 * 앱이 쓰는 기본 [TokenStore]. no-arg `Settings()`가 플랫폼별 저장소를 자동 초기화한다
 * (Android는 androidx-startup Context, iOS는 NSUserDefaults) — Context 배선 불필요.
 */
fun createTokenStore(): TokenStore = SettingsTokenStore(Settings())
