package watson.bytecs.account.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청마다 Authorization 헤더의 Bearer 토큰을 검증해 SecurityContext에 인증을 채운다.
 * 토큰이 없거나 잘못돼도 여기서 401을 내지 않고 그대로 진행시킨다.
 * 접근 제어(익명 허용 vs 인증 필요)는 SecurityConfig가 경로별로 판단하므로, 필터는 인증 사실만 기록한다.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        resolveToken(request)?.let { token ->
            // 토큰 파싱 실패는 인증 없음으로 취급한다. 보호된 경로는 이후 진입점에서 401로 걸러진다.
            runCatching { jwtTokenProvider.parse(token) }
                .onSuccess { principal -> authenticate(principal, request) }
        }

        filterChain.doFilter(request, response)
    }

    private fun authenticate(principal: AuthenticatedUser, request: HttpServletRequest) {
        val authorities = listOf(SimpleGrantedAuthority(ROLE_PREFIX + principal.role.name))
        val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null

        return header.substring(BEARER_PREFIX.length)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val ROLE_PREFIX = "ROLE_"
    }
}
