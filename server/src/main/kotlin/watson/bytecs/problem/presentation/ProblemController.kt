package watson.bytecs.problem.presentation

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.problem.application.ProblemService
import watson.bytecs.problem.application.dto.AttemptResponse
import watson.bytecs.problem.application.dto.NextProblemResponse
import watson.bytecs.problem.presentation.request.AttemptRequest

@RestController
@RequestMapping("/api/problems")
class ProblemController(
    private val problemService: ProblemService,
    private val requestMapper: ProblemRequestMapper,
) {

    /**
     * 추가 연습의 다음 문제를 준다. 문제 조회는 permitAll이라 토큰 없이도 열려 있으므로 principal은 nullable이다.
     * 실앱의 게스트는 항상 토큰을 보유하므로 보통 userId가 실려 개인화(푼 문제 제외 등)되고, 무토큰이면 전체 무작위로 폴백한다.
     */
    @GetMapping("/next")
    fun getNextProblem(
        @AuthenticationPrincipal user: AuthenticatedUser?,
    ): NextProblemResponse =
        problemService.findNextProblem(user?.userId)

    @PostMapping("/{id}/attempts")
    fun submitAttempt(
        @PathVariable id: Long,
        @Valid @RequestBody request: AttemptRequest,
    ): AttemptResponse {
        val answer = requestMapper.toAnswerText(request)
        return problemService.submitAnswer(id, answer)
    }
}
