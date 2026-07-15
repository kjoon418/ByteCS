package watson.bytecs.scrap.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 사용자 본인이 스크랩하지 않은 문제의 상세를 열람하려 할 때 던지는 예외.
 * 타인의 스크랩·미스크랩 문제를 '없음(404)'으로 응답해 사용자 격리를 지킨다(존재 여부를 흘리지 않는다).
 */
class ScrapNotFoundException private constructor(
    message: String,
) : ByteCsException(ErrorCode.SCRAP_NOT_FOUND, message) {

    companion object {
        fun forProblem(problemId: Long): ScrapNotFoundException =
            ScrapNotFoundException("스크랩한 문제를 찾을 수 없습니다. problemId = $problemId")
    }
}
