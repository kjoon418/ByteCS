package watson.bytecs.session.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 아직 열 수 없는 정답 공개(안전판)를 요청했을 때 던지는 예외.
 * 안전판은 최소 한 번 비정답을 제출한 뒤에만 열 수 있어, 시도 없이 정답을 흘리는 것을 막는다(→ 409 Conflict).
 */
class RevealNotAllowedException private constructor(
    message: String,
) : ByteCsException(ErrorCode.REVEAL_NOT_ALLOWED, message) {

    companion object {
        fun beforeAnyWrongAttempt(): RevealNotAllowedException =
            RevealNotAllowedException("한 번 이상 시도한 뒤에야 정답을 공개할 수 있습니다.")
    }
}
