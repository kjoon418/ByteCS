package watson.bytecs.admin

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.RawPassword
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository

/**
 * 기동 시 관리자 계정을 보장하는 부트스트랩.
 * 관리자 페이지는 자가 가입이 없으므로, 운영자는 환경변수(BYTECS_ADMIN_EMAIL·BYTECS_ADMIN_PASSWORD)로만
 * 관리자 계정을 만든다. 두 값이 모두 비어 있으면 아무것도 하지 않는다(관리자 기능이 잠긴 채 정상 기동).
 * 잘못된 설정(절반만 설정·형식 위반·이메일 충돌)은 조용히 넘기지 않고 기동 실패로 전환한다
 * ([watson.bytecs.config.JwtSecretGuard]와 같은 안전 실패 원칙).
 */
@Component
class AdminAccountBootstrap(
    @Value("\${bytecs.admin.email:}") private val adminEmail: String,
    @Value("\${bytecs.admin.password:}") private val adminPassword: String,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            return
        }
        check(adminEmail.isNotBlank() && adminPassword.isNotBlank()) { PARTIAL_CONFIG_MESSAGE }

        // 가입 흐름과 같은 VO로 검증·정규화한다(형식 위반이면 여기서 기동 실패).
        val email = Email(adminEmail)
        val password = RawPassword(adminPassword)

        val existing = userRepository.findByEmail(email.value)
        if (existing != null) {
            check(existing.role == UserRole.ADMIN) { EMAIL_ALREADY_TAKEN_MESSAGE }
            return
        }

        userRepository.save(User.createAdmin(email, passwordEncoder.encode(password.value)))
    }

    companion object {
        const val PARTIAL_CONFIG_MESSAGE =
            "관리자 부트스트랩은 이메일(BYTECS_ADMIN_EMAIL)과 비밀번호(BYTECS_ADMIN_PASSWORD)를 모두 설정해야 합니다."
        const val EMAIL_ALREADY_TAKEN_MESSAGE =
            "관리자 부트스트랩 이메일이 이미 일반 계정으로 사용 중입니다. 다른 이메일을 지정하세요."
    }
}
