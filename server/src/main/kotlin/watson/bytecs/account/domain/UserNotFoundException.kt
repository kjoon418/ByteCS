package watson.bytecs.account.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 요청한 사용자를 찾을 수 없을 때 던지는 예외.
 */
class UserNotFoundException private constructor(
    message: String,
) : ByteCsException(ErrorCode.USER_NOT_FOUND, message) {

    companion object {
        fun byId(userId: Long): UserNotFoundException =
            UserNotFoundException("사용자를 찾을 수 없습니다. id = $userId")
    }
}
