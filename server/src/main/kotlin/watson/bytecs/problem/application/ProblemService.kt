package watson.bytecs.problem.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.problem.application.dto.AttemptResponse
import watson.bytecs.problem.application.dto.NextProblemResponse
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository

/**
 * 문제 조회·답 제출을 담당한다.
 * 판정 로직은 도메인(Problem.judge)에 위임하고, 서비스는 조회·응답 변환만 조율한다.
 * 이 슬라이스는 조회와 판정뿐이라 쓰기가 없으므로 읽기 전용 트랜잭션 하나로 묶는다.
 */
@Service
@Transactional(readOnly = true)
class ProblemService(
    private val problemRepository: ProblemRepository,
    private val responseMapper: ProblemResponseMapper,
) {

    fun findNextProblem(): NextProblemResponse {
        val problem = problemRepository.findFirstByOrderByIdAsc()
            ?: throw ProblemNotFoundException.noneAvailable()

        return responseMapper.toNextProblemResponse(problem)
    }

    fun submitAnswer(problemId: Long, answer: AnswerText): AttemptResponse {
        val problem = problemRepository.findById(problemId)
            .orElseThrow { ProblemNotFoundException.byId(problemId) }

        val judgement = problem.judge(answer)
        return responseMapper.toAttemptResponse(problem, judgement)
    }
}
