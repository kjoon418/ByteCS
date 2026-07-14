package watson.bytecs.session.presentation

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.session.application.SessionService
import watson.bytecs.session.application.dto.PastItemResponse
import watson.bytecs.session.application.dto.RevealResponse
import watson.bytecs.session.application.dto.SessionAttemptResponse
import watson.bytecs.session.application.dto.SessionStateResponse
import watson.bytecs.session.presentation.request.SessionAttemptRequest

/**
 * 인증된 사용자 본인의 '오늘의 한입'(일일 세션)을 다룬다.
 * 세션은 토큰에서 복원한 principal(userId)로만 결정해, 다른 사용자의 세션을 조작·열람할 수 없게 한다.
 * 세션 경로는 SecurityConfig의 permitAll 목록에 없으므로 기본 규칙에 따라 인증이 강제된다.
 */
@RestController
@RequestMapping("/api/sessions")
class SessionController(
    private val sessionService: SessionService,
    private val requestMapper: SessionRequestMapper,
) {

    @GetMapping("/today")
    fun getToday(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): SessionStateResponse =
        sessionService.getOrCreateToday(user.userId)

    @PostMapping("/today/attempts")
    fun submitAttempt(
        @Valid @RequestBody request: SessionAttemptRequest,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): SessionAttemptResponse {
        val answer = requestMapper.toAnswerText(request)
        return sessionService.submitAnswer(user.userId, answer)
    }

    @PostMapping("/today/reveal")
    fun reveal(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): RevealResponse =
        sessionService.reveal(user.userId)

    @GetMapping("/today/items/{position}")
    fun getPastItem(
        @PathVariable position: Int,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): PastItemResponse =
        sessionService.getPastItem(user.userId, position)
}
