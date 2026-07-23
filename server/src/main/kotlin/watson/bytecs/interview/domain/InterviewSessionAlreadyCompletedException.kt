package watson.bytecs.interview.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/** 이미 완료된 면접 세션에 답을 제출하려 할 때 던진다(재제출 없음 — 계획 §3.3). */
class InterviewSessionAlreadyCompletedException private constructor(
    message: String,
) : ByteCsException(ErrorCode.INTERVIEW_SESSION_ALREADY_COMPLETED, message) {

    companion object {
        fun forAnswer(): InterviewSessionAlreadyCompletedException =
            InterviewSessionAlreadyCompletedException("이미 완료된 면접 세션입니다.")
    }
}
