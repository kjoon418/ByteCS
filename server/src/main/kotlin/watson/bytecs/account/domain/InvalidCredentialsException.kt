package watson.bytecs.account.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 로그인 자격 증명이 올바르지 않을 때 던지는 예외.
 * 어떤 정보가 틀렸는지(이메일 없음 vs 비밀번호 불일치) 구분해 노출하지 않는다(계정 열거 방지).
 */
class InvalidCredentialsException :
    ByteCsException(ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.")
