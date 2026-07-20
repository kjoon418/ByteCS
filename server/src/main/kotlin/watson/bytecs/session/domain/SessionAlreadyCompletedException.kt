package watson.bytecs.session.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 이미 완료된 세션에 답 제출·정답 공개를 시도했을 때 던지는 예외.
 * 완료된 세션은 더 이상 진행할 수 없고, 더 풀려면 '조금 더 풀기'로 새 세션을 시작한다(D6·D9 일원화 → 409 Conflict).
 */
class SessionAlreadyCompletedException private constructor(
    message: String,
) : ByteCsException(ErrorCode.SESSION_ALREADY_COMPLETED, message) {

    companion object {
        fun forAttempt(): SessionAlreadyCompletedException =
            SessionAlreadyCompletedException("이미 완료된 세션입니다. '조금 더 풀기'로 새 세션을 시작해 보세요.")

        fun forReveal(): SessionAlreadyCompletedException =
            SessionAlreadyCompletedException("이미 완료된 세션이라 공개할 본 문제가 없습니다.")

        fun forHintReveal(): SessionAlreadyCompletedException =
            SessionAlreadyCompletedException("이미 완료된 세션이라 힌트를 열 본 문제가 없습니다.")
    }
}
