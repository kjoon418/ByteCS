package watson.bytecs.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * 커밋된 기본 개발 시크릿으로 운영 프로파일에 기동하는 것을 막는 부팅 가드.
 * 시크릿 누락은 조용히 넘어가면 위조 가능한 토큰으로 이어지므로, 안전 실패(부팅 중단)로 전환한다.
 * 판정은 순수 함수(isForbiddenSecret)로 분리해 전체 부팅 없이 단위 테스트할 수 있게 한다.
 */
@Component
class JwtSecretGuard(
    @Value("\${jwt.secret}") private val secret: String,
    private val environment: Environment,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        if (isForbiddenSecret(environment.activeProfiles.toList(), secret)) {
            throw IllegalStateException(FORBIDDEN_SECRET_MESSAGE)
        }
    }

    companion object {
        // application.yml에 커밋된 로컬 개발용 기본 시크릿. 운영에서는 절대 사용해선 안 된다.
        const val DEFAULT_DEV_SECRET =
            "bytecs-local-dev-secret-key-please-change-in-production-0123456789"
        const val FORBIDDEN_SECRET_MESSAGE =
            "운영 프로파일에서는 JWT_SECRET을 반드시 주입해야 합니다. 기본 개발 시크릿으로 기동할 수 없습니다."

        // 기본 시크릿 사용이 허용되는 개발 프로파일. 이 외(운영·프로파일 미지정)에서는 기본 시크릿을 금지한다.
        private val DEV_PROFILES = setOf("local", "test")

        fun isForbiddenSecret(activeProfiles: Collection<String>, secret: String): Boolean {
            if (secret != DEFAULT_DEV_SECRET) return false

            return activeProfiles.none { it in DEV_PROFILES }
        }
    }
}
