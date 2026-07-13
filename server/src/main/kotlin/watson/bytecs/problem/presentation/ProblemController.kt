package watson.bytecs.problem.presentation

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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

    @GetMapping("/next")
    fun getNextProblem(): NextProblemResponse =
        problemService.findNextProblem()

    @PostMapping("/{id}/attempts")
    fun submitAttempt(
        @PathVariable id: Long,
        @Valid @RequestBody request: AttemptRequest,
    ): AttemptResponse {
        val answer = requestMapper.toAnswerText(request)
        return problemService.submitAnswer(id, answer)
    }
}
