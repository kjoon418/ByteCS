package watson.bytecs

import androidx.compose.runtime.Composable

/**
 * 브라우저(wasmJs)는 no-op다 — iOS·데스크톱 테스트 타깃과 같은 트레이드오프.
 *
 * 브라우저 뒤로가기(popstate)는 앱의 명시적 백스택과 무관하게 탭을 이탈시키므로, 여기에 곧장
 * 연결하면 오히려 앱을 벗어난다. popstate ↔ 백스택 연동은 계획의 백로그(§8)로 미뤘다.
 */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
