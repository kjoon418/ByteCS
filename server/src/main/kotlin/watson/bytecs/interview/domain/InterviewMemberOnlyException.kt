package watson.bytecs.interview.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/** 게스트가 면접 세션(회원 전용, DI3)에 접근하려 할 때 던진다. */
class InterviewMemberOnlyException private constructor(
    message: String,
) : ByteCsException(ErrorCode.INTERVIEW_MEMBER_ONLY, message) {

    companion object {
        fun forGuest(): InterviewMemberOnlyException =
            InterviewMemberOnlyException("면접 세션은 회원만 이용할 수 있습니다. 가입하면 이용할 수 있어요.")
    }
}
