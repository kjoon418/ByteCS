package watson.bytecs.session.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 오늘의 세션을 찾을 수 없을 때 던지는 예외.
 * 답 제출·정답 공개·지난 문제 조회는 오늘 세션이 있어야 성립하므로, 없으면 404로 응답한다.
 */
class SessionNotFoundException private constructor(
    message: String,
) : ByteCsException(ErrorCode.SESSION_NOT_FOUND, message) {

    companion object {
        fun forToday(): SessionNotFoundException =
            SessionNotFoundException("오늘의 세션이 없습니다. 먼저 오늘의 세션을 시작하세요.")
    }
}
