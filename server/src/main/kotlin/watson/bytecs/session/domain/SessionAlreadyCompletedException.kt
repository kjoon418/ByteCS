package watson.bytecs.session.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 이미 완료된 세션에 답 제출·정답 공개를 시도했을 때 던지는 예외.
 * 완료된 세션은 더 이상 진행할 수 없고, 추가 학습은 별도의 무상태 연습 API 소관이다(→ 409 Conflict).
 */
class SessionAlreadyCompletedException private constructor(
    message: String,
) : ByteCsException(ErrorCode.SESSION_ALREADY_COMPLETED, message) {

    companion object {
        fun forAttempt(): SessionAlreadyCompletedException =
            SessionAlreadyCompletedException("이미 완료된 세션입니다. 추가 연습을 이용하세요.")

        fun forReveal(): SessionAlreadyCompletedException =
            SessionAlreadyCompletedException("이미 완료된 세션이라 공개할 본 문제가 없습니다.")

        fun forHintReveal(): SessionAlreadyCompletedException =
            SessionAlreadyCompletedException("이미 완료된 세션이라 힌트를 열 본 문제가 없습니다.")
    }
}
