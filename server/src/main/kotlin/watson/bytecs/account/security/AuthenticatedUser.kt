package watson.bytecs.account.security

import watson.bytecs.account.domain.UserRole

/**
 * 인증된 요청 주체. SecurityContext의 principal로 실려, 컨트롤러가 세션 없이도 사용자 식별자를 얻게 한다.
 * 토큰에서 복원한 최소 정보(식별자·역할)만 담아 서버 상태를 무상태로 유지한다.
 */
data class AuthenticatedUser(
    val userId: Long,
    val role: UserRole,
)
