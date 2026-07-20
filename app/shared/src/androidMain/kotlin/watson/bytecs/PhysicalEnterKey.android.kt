package watson.bytecs

import androidx.compose.runtime.Composable

/** 모바일은 물리 Enter 키 개념이 없다(소프트 키보드의 완료 액션은 입력칸이 처리) — no-op. */
@Composable
actual fun PhysicalEnterKey(enabled: Boolean, onEnter: () -> Unit) = Unit
