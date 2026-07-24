package watson.bytecs.interview.presentation

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.interview.application.InterviewSessionService
import watson.bytecs.interview.application.dto.InterviewAnswerResponse
import watson.bytecs.interview.application.dto.InterviewHintRevealResponse
import watson.bytecs.interview.application.dto.InterviewSessionResponse
import watson.bytecs.interview.application.dto.InterviewStatusResponse
import watson.bytecs.interview.presentation.request.InterviewAnswerRequest
import watson.bytecs.interview.presentation.request.InterviewHintRevealRequest

/**
 * 인증된 사용자 본인의 면접 세션(계획 §3.3)을 다룬다. 일반 세션과 마찬가지로 principal(userId)로만 대상을 정한다.
 * 회원 전용 규칙은 서비스가 판단해 403([watson.bytecs.interview.domain.InterviewMemberOnlyException])으로 응답한다 —
 * `/status`만 게스트도 조회할 수 있다(가입 유도 데이터, 서비스 내부에서 게스트 분기).
 */
@RestController
@RequestMapping("/api/interview")
class InterviewController(
    private val interviewSessionService: InterviewSessionService,
) {

    @GetMapping("/status")
    fun getStatus(@AuthenticationPrincipal user: AuthenticatedUser): InterviewStatusResponse =
        interviewSessionService.getStatus(user.userId)

    @PostMapping("/sessions/today")
    fun createTodaySession(@AuthenticationPrincipal user: AuthenticatedUser): InterviewSessionResponse =
        interviewSessionService.createTodaySession(user.userId)

    @GetMapping("/sessions/today")
    fun getTodaySession(@AuthenticationPrincipal user: AuthenticatedUser): InterviewSessionResponse =
        interviewSessionService.getTodaySession(user.userId)

    @PostMapping("/sessions/today/answers")
    fun submitAnswer(
        @Valid @RequestBody request: InterviewAnswerRequest,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): InterviewAnswerResponse =
        interviewSessionService.submitAnswer(user.userId, requireNotNull(request.explanation))

    @PostMapping("/sessions/today/hints/reveal")
    fun revealHint(
        @Valid @RequestBody request: InterviewHintRevealRequest,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): InterviewHintRevealResponse =
        interviewSessionService.revealHint(user.userId, request.revealedCount!!)
}
