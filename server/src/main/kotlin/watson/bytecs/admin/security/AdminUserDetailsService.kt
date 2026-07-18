package watson.bytecs.admin.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository

/**
 * 관리자 폼 로그인 전용 UserDetailsService.
 * ADMIN 역할이 아닌 계정(회원·게스트)은 여기서부터 차단해, 일반 회원 자격으로는 관리자 세션 자체가
 * 만들어지지 않게 한다(인가 규칙 hasRole(ADMIN)과 별개의 심층 방어).
 * 실패 사유는 구분하지 않는다 — 폼 로그인 기본 동작이 동일한 실패 응답으로 수렴시켜 계정 열거를 막는다.
 */
@Service
class AdminUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
        if (user == null || user.role != UserRole.ADMIN) {
            throw UsernameNotFoundException(NOT_ADMIN_MESSAGE)
        }
        val email = user.email
        val passwordHash = user.passwordHash
        if (email == null || passwordHash == null) {
            throw UsernameNotFoundException(NOT_ADMIN_MESSAGE)
        }

        return org.springframework.security.core.userdetails.User(
            email,
            passwordHash,
            listOf(SimpleGrantedAuthority(ADMIN_AUTHORITY)),
        )
    }

    companion object {
        const val ADMIN_AUTHORITY = "ROLE_ADMIN"
        const val NOT_ADMIN_MESSAGE = "관리자 계정이 아닙니다."
    }
}
