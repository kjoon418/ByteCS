package watson.bytecs.account.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import watson.bytecs.account.domain.UserRole
import java.util.Date

/**
 * JWT 발급·검증을 담당한다.
 * 인증을 무상태로 만들어 서버가 세션을 보관하지 않게 하고, 토큰에 사용자 식별자·역할만 실어 최소 권한을 유지한다.
 */
@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.expiration-seconds}") private val expirationSeconds: Long,
) {
    // 대칭키(HS256)를 앱 기동 시 한 번만 만들어 재사용한다.
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun issue(userId: Long, role: UserRole): String {
        val now = Date()
        val expiration = Date(now.time + expirationSeconds * MILLIS_PER_SECOND)

        return Jwts.builder()
            .subject(userId.toString())
            .claim(ROLE_CLAIM, role.name)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact()
    }

    /**
     * 토큰을 검증·파싱해 인증 주체를 복원한다.
     * 서명 불일치·만료·형식 오류 등 어떤 실패든 예외로 전파해, 호출자가 인증 실패로 일관되게 처리하게 한다.
     */
    fun parse(token: String): AuthenticatedUser {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        val userId = claims.subject.toLong()
        val role = UserRole.valueOf(claims.get(ROLE_CLAIM, String::class.java))

        return AuthenticatedUser(userId, role)
    }

    companion object {
        private const val ROLE_CLAIM = "role"
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
