package watson.bytecs.ui.theme

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면 테마 선택(§화면 06). 라이트/다크/시스템 셋 중 하나를 앱 전역으로 유지하고 기기에 영속한다.
 */
enum class ThemeMode {
    /** 항상 라이트. */
    LIGHT,

    /** 항상 다크. */
    DARK,

    /** 기기 시스템 설정을 따른다(기본값). */
    SYSTEM,
}

/**
 * 테마 선택의 단일 출처. [mode]를 노출하고 변경 시 [Settings]에 저장해, 앱 재실행에도 선택을 잇는다.
 * 저장소를 주입 가능하게 두어(테스트는 MapSettings) 실기기 스토리지 없이 검증한다.
 */
class ThemeController(
    private val settings: Settings,
) {
    private val _mode = MutableStateFlow(load())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        settings.putString(KEY, mode.name)
    }

    private fun load(): ThemeMode {
        val saved = settings.getStringOrNull(KEY) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.find { it.name == saved } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}

/** 앱이 쓰는 기본 [ThemeController]. no-arg `Settings()`가 플랫폼 저장소를 자동 초기화한다. */
fun createThemeController(): ThemeController = ThemeController(Settings())
