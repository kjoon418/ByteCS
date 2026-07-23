package watson.bytecs.interview.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/** 오늘 진행 중인 면접 세션이 없을 때 던진다(재개 조회 전용 — 새 세션은 생성 API로 시작한다). */
class InterviewSessionNotFoundException private constructor(
    message: String,
) : ByteCsException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, message) {

    companion object {
        fun forToday(): InterviewSessionNotFoundException =
            InterviewSessionNotFoundException("오늘 진행 중인 면접 세션이 없습니다.")
    }
}
