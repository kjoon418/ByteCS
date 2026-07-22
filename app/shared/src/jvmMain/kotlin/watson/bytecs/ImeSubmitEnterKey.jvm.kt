package watson.bytecs

import androidx.compose.runtime.Composable

/** JVM은 Compose UI 테스트 실행기 전용이라 IME 조합 Enter 우회가 필요 없다 — no-op. */
@Composable
actual fun ImeSubmitEnterKey(enabled: Boolean, onSubmit: () -> Unit) = Unit
