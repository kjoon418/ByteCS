package watson.bytecs.extrastudy.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 열린(이어 풀) 항목이 없는데 답 제출·정답 공개·힌트 공개를 시도했을 때 던지는 예외.
 * 방금 풀어 비웠거나 아직 뽑지 않은 상태에서의 경합이며, 무낙인이라 오류 어휘 없이 클라가 재동기화하도록 유도한다(→ 409 Conflict).
 */
class ExtraStudyNoOpenItemException private constructor(
    message: String,
) : ByteCsException(ErrorCode.EXTRA_STUDY_NO_OPEN_ITEM, message) {

    companion object {
        fun forAttempt(): ExtraStudyNoOpenItemException =
            ExtraStudyNoOpenItemException("지금 풀 문제가 없습니다. 다시 불러와 주세요.")

        fun forReveal(): ExtraStudyNoOpenItemException =
            ExtraStudyNoOpenItemException("지금 공개할 문제가 없습니다. 다시 불러와 주세요.")

        fun forHintReveal(): ExtraStudyNoOpenItemException =
            ExtraStudyNoOpenItemException("지금 힌트를 열 문제가 없습니다. 다시 불러와 주세요.")
    }
}
