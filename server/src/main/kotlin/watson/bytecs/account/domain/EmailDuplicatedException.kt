package watson.bytecs.account.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 이미 사용 중인 이메일로 가입을 시도할 때 던지는 예외.
 */
class EmailDuplicatedException(
    email: Email,
) : ByteCsException(ErrorCode.EMAIL_DUPLICATED, "이미 사용 중인 이메일입니다.")
