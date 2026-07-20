package watson.bytecs

import androidx.compose.runtime.Composable

/** iOS는 자체 엣지 스와이프 뒤로가기 관례를 따른다(이 슬라이스 범위 밖) — no-op. */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
