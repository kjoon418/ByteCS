package watson.bytecs

import androidx.compose.runtime.Composable

/**
 * 입력칸이 포커스를 가진 동안, 한글 IME **조합 중**에 누른 Enter를 제출로 잇는다.
 *
 * ⭐️ 웹(skiko 캔버스) 전용 문제: 한글을 조합(composition)하는 중에 누른 Enter는, 브라우저가 조합을 먼저
 * 확정(compositionend)한 **뒤** isComposing=false인 Enter keydown을 내는데, skiko는 이 Enter를 조합 확정과
 * 같은 배치라 삼켜서 [androidx.compose.ui.text.input.ImeAction]으로 넘기지 않는다. 그래서 사용자가 Enter를 한 번
 * 누르면 마지막 글자만 완성되고, **두 번째** Enter(조합과 무관한 깨끗한 Enter)라야 제출(다음 단계)이 일어난다.
 * 이 훅은 "조합 확정(compositionend)과 Enter가 짧은 시간창 안에 함께 발생"함을 감지해 [onSubmit]을 한 번
 * 부른다 — skiko가 놓친 빈틈만 메운다. 두 이벤트가 늘 같은 프레임에 붙어 오지는 않아(드물게 프레임 경계가
 * 끼거나 순서가 뒤집힌다) 시각 기록 기반 짝짓기로 그 지터를 견딘다(wasmJs actual 참고).
 *
 * 조합과 무관한(깨끗한) Enter는 손대지 않는다. 그건 이미 ImeAction 경로가 처리하므로, 손대면 한 번의 Enter가
 * 두 번 제출된다. 이 훅과 ImeAction 경로는 정확히 상호 배타(조합 확정 직후 vs 그 외)라 이중 제출이 없다.
 *
 * 물리 키보드/IME 조합이 이 문제를 일으키지 않는 모바일(Android·iOS)과 테스트 실행기(JVM)는 no-op다 —
 * 소프트 키보드의 완료 액션은 글자 확정과 분리돼 있어 한 번에 동작하고, 웹 동작은 브라우저 스모크로 검증한다.
 *
 * @param enabled 이 입력칸이 지금 제출을 받는 상태(보통 포커스 중)일 때만 true.
 * @param onSubmit 조합 확정 뒤 부를 제출 동작(입력칸의 IME 제출과 같은 것).
 */
@Composable
expect fun ImeSubmitEnterKey(enabled: Boolean, onSubmit: () -> Unit)
