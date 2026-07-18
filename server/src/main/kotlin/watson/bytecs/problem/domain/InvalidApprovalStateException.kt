package watson.bytecs.problem.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 콘텐츠 승인 상태 전이 규칙을 위반했거나(예: 초안에서 바로 승인),
 * 승인 요건(결정적 구조 검증 — 가드레일)을 충족하지 못했을 때 던지는 예외.
 */
class InvalidApprovalStateException(
    message: String,
) : ByteCsException(ErrorCode.INVALID_APPROVAL_STATE, message)
