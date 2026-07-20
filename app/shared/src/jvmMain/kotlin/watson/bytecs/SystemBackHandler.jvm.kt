package watson.bytecs

import androidx.compose.runtime.Composable

/** 데스크톱(JVM)은 Compose UI 테스트 실행기 전용이라 하드웨어 뒤로가기 개념이 없다 — no-op. */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
