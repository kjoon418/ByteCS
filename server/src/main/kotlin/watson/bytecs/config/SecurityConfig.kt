package watson.bytecs.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import watson.bytecs.account.security.JwtAuthenticationFilter
import watson.bytecs.common.error.ErrorCode
import watson.bytecs.common.error.ErrorResponse

/**
 * 무상태(JWT) 보안 설정.
 * 세션·CSRF·기본 로그인 폼을 모두 끄고, 경로별 접근 규칙과 JWT 필터만으로 인증을 구성한다.
 * 인증·인가 실패는 전역 예외 응답과 동일한 ErrorResponse(JSON)로 통일해 클라이언트가 일관되게 처리하게 한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
    private val environment: Environment,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // H2 콘솔은 로컬 개발에서만 iframe으로 뜨므로, 프레임 완화는 local 프로파일로만 한정한다.
        // 그 외(운영 등)에서는 기본값 DENY를 유지해 클릭재킹 표면을 남기지 않는다.
        val relaxFrameForH2Console = environment.activeProfiles.contains(LOCAL_PROFILE)

        http {
            // 무상태 API라 CSRF 토큰·세션·로그인 폼·기본 인증이 필요 없다.
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            headers {
                frameOptions {
                    if (relaxFrameForH2Console) {
                        sameOrigin = true
                    }
                }
            }

            authorizeHttpRequests {
                // 게스트 발급·인증·문제 조회는 로그인 전에도 열려 있어야 한다(부담 없는 학습 시작).
                authorize(HttpMethod.POST, "/api/guests", permitAll)
                authorize("/api/auth/**", permitAll)
                // 신고·스크랩은 사용자 소유 동작이라 인증이 필요하다. /api/problems/** permitAll보다 먼저 선언해(첫 매칭 우선)
                // 이 하위 경로만 인증을 강제한다. 문제 자체의 조회(GET)는 여전히 permitAll이다.
                authorize(HttpMethod.POST, "/api/problems/*/reports", authenticated)
                authorize(HttpMethod.POST, "/api/problems/*/scraps", authenticated)
                authorize(HttpMethod.DELETE, "/api/problems/*/scraps", authenticated)
                authorize("/api/problems/**", permitAll)
                authorize("/h2-console/**", permitAll)
                // 웹 클라이언트 정적 번들(같은 오리진 서빙). GET만 열어 서빙 외 표면을 만들지 않는다.
                // 루트 레벨 자산(*.js/*.wasm 등)은 번들이 루트에 평평하게 떨어지고(해시 파일명 포함),
                // 폰트 등 중첩 리소스는 /composeResources/** 로 연다. 광범위 /** permit은 금지 —
                // /api 보호가 기본값(anyRequest authenticated)으로 유지되어야 한다.
                authorize(HttpMethod.GET, "/", permitAll)
                authorize(HttpMethod.GET, "/index.html", permitAll)
                authorize(HttpMethod.GET, "/*.js", permitAll)
                authorize(HttpMethod.GET, "/*.wasm", permitAll)
                authorize(HttpMethod.GET, "/*.css", permitAll)
                authorize(HttpMethod.GET, "/*.map", permitAll)
                authorize(HttpMethod.GET, "/composeResources/**", permitAll)
                authorize(HttpMethod.GET, "/favicon.ico", permitAll)
                authorize(anyRequest, authenticated)
            }

            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthenticationFilter)

            exceptionHandling {
                authenticationEntryPoint = jwtAuthenticationEntryPoint()
                accessDeniedHandler = jwtAccessDeniedHandler()
            }
        }

        return http.build()
    }

    /** 인증 없이 보호된 자원에 접근하면 401을 ErrorResponse(JSON)로 반환한다. */
    private fun jwtAuthenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ ->
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")
        }

    /** 인증은 됐지만 권한이 부족하면 403을 ErrorResponse(JSON)로 반환한다. */
    private fun jwtAccessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { _, response, _ ->
            writeError(response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "접근 권한이 없습니다.")
        }

    private fun writeError(
        response: jakarta.servlet.http.HttpServletResponse,
        status: HttpStatus,
        errorCode: ErrorCode,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = "${MediaType.APPLICATION_JSON_VALUE};charset=UTF-8"
        objectMapper.writeValue(response.writer, ErrorResponse(message, errorCode))
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    companion object {
        private const val LOCAL_PROFILE = "local"
    }
}
