package watson.bytecs.account.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SettingsTokenStore가 토큰을 저장·조회·삭제하는지 MapSettings(인메모리)로 검증한다.
 */
class TokenStoreTest {

    @Test
    fun get_returnsNull_whenEmpty() {
        val store = SettingsTokenStore(MapSettings())
        assertNull(store.get(), "저장 전에는 null")
    }

    @Test
    fun set_thenGet_returnsToken() {
        val store = SettingsTokenStore(MapSettings())
        store.set("jwt-abc")
        assertEquals("jwt-abc", store.get())
    }

    @Test
    fun set_overwritesPreviousToken() {
        val store = SettingsTokenStore(MapSettings())
        store.set("first")
        store.set("second")
        assertEquals("second", store.get(), "새 토큰이 이전 토큰을 덮어써야 한다")
    }

    @Test
    fun clear_removesToken() {
        val store = SettingsTokenStore(MapSettings())
        store.set("jwt-abc")
        store.clear()
        assertNull(store.get(), "삭제 후에는 null")
    }
}
