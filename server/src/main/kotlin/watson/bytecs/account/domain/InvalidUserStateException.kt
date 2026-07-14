package watson.bytecs.account.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 사용자 상태 전이 규칙을 위반했을 때 던지는 예외(예: 이미 회원인데 다시 가입).
 */
class InvalidUserStateException(
    message: String,
) : ByteCsException(ErrorCode.INVALID_USER_STATE, message)
