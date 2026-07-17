package watson.bytecs.extrastudy.presentation

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.extrastudy.application.ExtraStudyService
import watson.bytecs.extrastudy.application.dto.ExtraStudyAttemptResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyCurrentResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyHintRevealResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyRevealResponse
import watson.bytecs.extrastudy.presentation.request.ExtraStudyAttemptRequest
import watson.bytecs.extrastudy.presentation.request.ExtraStudyHintRevealRequest

/**
 * 인증된 사용자 본인의 '추가 학습'(Extra Study)을 다룬다.
 * 자원은 토큰에서 복원한 principal(userId)로만 결정해, 다른 사용자의 추가 학습을 조작·열람할 수 없게 한다.
 * 경로는 SecurityConfig의 permitAll 목록에 없으므로 기본 규칙(anyRequest→authenticated)에 따라 게스트 토큰 포함 인증이 강제된다.
 */
@RestController
@RequestMapping("/api/extra-study")
class ExtraStudyController(
    private val extraStudyService: ExtraStudyService,
    private val requestMapper: ExtraStudyRequestMapper,
) {

    @GetMapping("/current")
    fun getCurrent(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ExtraStudyCurrentResponse =
        extraStudyService.getCurrent(user.userId)

    @PostMapping("/attempts")
    fun submitAttempt(
        @Valid @RequestBody request: ExtraStudyAttemptRequest,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ExtraStudyAttemptResponse {
        val answer = requestMapper.toAnswerText(request)
        return extraStudyService.submitAnswer(user.userId, answer)
    }

    @PostMapping("/reveal")
    fun reveal(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ExtraStudyRevealResponse =
        extraStudyService.reveal(user.userId)

    @PostMapping("/hints/reveal")
    fun revealHint(
        @Valid @RequestBody request: ExtraStudyHintRevealRequest,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ExtraStudyHintRevealResponse =
        extraStudyService.revealHint(user.userId, request.revealedCount!!)
}
