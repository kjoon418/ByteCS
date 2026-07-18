package watson.bytecs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import watson.bytecs.admin.security.AdminUserDetailsService

/**
 * 관리자 페이지(`/admin` 하위 경로) 전용 보안 체인.
 * 무상태 JWT API 체인([SecurityConfig])과 달리 브라우저 대상 서버 렌더링 페이지라
 * 폼 로그인 + 세션 + CSRF(기본 활성)를 쓴다. securityMatcher와 @Order(0)으로 이 체인이
 * /admin 경로만 선점하므로, 기존 API 체인의 규칙·응답 형식에는 영향을 주지 않는다.
 */
@Configuration
class AdminSecurityConfig(
    private val adminUserDetailsService: AdminUserDetailsService,
    private val passwordEncoder: PasswordEncoder,
) {

    @Bean
    @Order(0)
    fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authenticationProvider(adminAuthenticationProvider())

        http {
            securityMatcher("/admin/**")

            authorizeHttpRequests {
                authorize(anyRequest, hasRole("ADMIN"))
            }

            formLogin {
                loginPage = "/admin/login"
                loginProcessingUrl = "/admin/login"
                // 미인증 진입점이 저장한 원 요청이 있으면 그쪽으로, 없으면 관리자 홈으로 보낸다.
                defaultSuccessUrl("/admin", false)
                permitAll()
            }

            logout {
                logoutUrl = "/admin/logout"
                logoutSuccessUrl = "/admin/login?logout"
            }
        }

        return http.build()
    }

    private fun adminAuthenticationProvider(): DaoAuthenticationProvider =
        DaoAuthenticationProvider(adminUserDetailsService).apply {
            setPasswordEncoder(passwordEncoder)
        }
}
