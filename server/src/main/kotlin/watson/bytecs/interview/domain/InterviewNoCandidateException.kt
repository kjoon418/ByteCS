package watson.bytecs.interview.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/** 승급 후보 개념이 없어(레벨≥1 개념이 없거나 승인된 면접 질문이 없음) 면접 세션을 시작할 수 없을 때 던진다. */
class InterviewNoCandidateException private constructor(
    message: String,
) : ByteCsException(ErrorCode.INTERVIEW_NO_CANDIDATE, message) {

    companion object {
        fun noEligibleConcept(): InterviewNoCandidateException =
            InterviewNoCandidateException("아직 면접 연습을 시작할 개념이 없습니다. 오늘의 한입에서 개념을 맞혀보세요.")
    }
}
