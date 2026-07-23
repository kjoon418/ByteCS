package watson.bytecs.interview.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/** 오늘의 면접 세션 쿼터를 소진했을 때 던진다(채점 성공 호출이 포함된 세션 생성만 차감 — 계획 §3.3). */
class InterviewQuotaExceededException private constructor(
    message: String,
) : ByteCsException(ErrorCode.INTERVIEW_QUOTA_EXCEEDED, message) {

    companion object {
        fun forToday(): InterviewQuotaExceededException =
            InterviewQuotaExceededException("오늘의 면접 세션을 모두 사용했습니다. 내일 다시 시도해 보세요.")
    }
}
