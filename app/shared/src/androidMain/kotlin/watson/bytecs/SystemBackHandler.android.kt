package watson.bytecs

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * 안드로이드: 액티비티의 `OnBackPressedDispatcher`에 연결한다(activity-compose `BackHandler`).
 * [enabled]가 false면(홈만 남음) 콜백을 걸지 않아 시스템 기본 동작(앱 종료)에 위임된다.
 */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
