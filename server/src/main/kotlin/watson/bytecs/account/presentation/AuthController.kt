package watson.bytecs.account.presentation

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.application.AccountService
import watson.bytecs.account.application.dto.TokenResponse
import watson.bytecs.account.presentation.request.LoginRequest
import watson.bytecs.account.presentation.request.RegisterRequest
import watson.bytecs.account.security.AuthenticatedUser

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val accountService: AccountService,
    private val requestMapper: AccountRequestMapper,
) {

    /**
     * 회원 가입. 게스트 토큰을 함께 보내면 그 게스트를 승격해 학습 상태를 승계한다.
     * 로그인 전에도 열린 경로라 principal이 없을 수 있으므로, 잘못된 principal 타입에 예외 없이 null로 받는다.
     */
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        @AuthenticationPrincipal(errorOnInvalidType = false) principal: AuthenticatedUser?,
    ): TokenResponse {
        val email = requestMapper.toEmail(request)
        val rawPassword = requestMapper.toRawPassword(request)

        return accountService.register(email, rawPassword, principal?.userId)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): TokenResponse {
        val email = requestMapper.toEmail(request)
        val rawPassword = requestMapper.toRawPassword(request)

        return accountService.login(email, rawPassword)
    }
}
