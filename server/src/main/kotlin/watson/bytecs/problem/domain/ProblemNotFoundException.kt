package watson.bytecs.problem.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 요청한 문제를 찾을 수 없을 때 던지는 예외.
 */
class ProblemNotFoundException private constructor(
    message: String,
) : ByteCsException(ErrorCode.PROBLEM_NOT_FOUND, message) {

    companion object {
        fun byId(problemId: Long): ProblemNotFoundException =
            ProblemNotFoundException("문제를 찾을 수 없습니다. id = $problemId")

        fun noneAvailable(): ProblemNotFoundException =
            ProblemNotFoundException("풀 수 있는 문제가 없습니다.")
    }
}
