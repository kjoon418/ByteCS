package watson.bytecs

import androidx.compose.runtime.Composable

/** JVM은 Compose UI 테스트 실행기 전용이라 물리 Enter 훅이 필요 없다 — no-op. */
@Composable
actual fun PhysicalEnterKey(enabled: Boolean, onEnter: () -> Unit) = Unit
