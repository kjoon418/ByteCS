package watson.bytecs

import androidx.compose.runtime.Composable

/** 모바일 소프트 키보드는 완료 액션이 글자 확정과 분리돼 한 번에 동작한다(이 문제 없음) — no-op. */
@Composable
actual fun ImeSubmitEnterKey(enabled: Boolean, onSubmit: () -> Unit) = Unit
