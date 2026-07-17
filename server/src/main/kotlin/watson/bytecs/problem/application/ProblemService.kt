package watson.bytecs.problem.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.problem.application.dto.AttemptResponse
import watson.bytecs.problem.application.dto.NextProblemResponse
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.LocalDate
import kotlin.random.Random

/**
 * 문제 조회·답 제출을 담당한다.
 * 판정 로직은 도메인(Problem.judge)에 위임하고, 서비스는 조회·응답 변환만 조율한다.
 * 이 슬라이스는 조회와 판정뿐이라 쓰기가 없으므로 읽기 전용 트랜잭션 하나로 묶는다.
 */
@Service
@Transactional(readOnly = true)
class ProblemService(
    private val problemRepository: ProblemRepository,
    private val sessionRepository: SessionRepository,
    private val reviewService: ReviewService,
    private val responseMapper: ProblemResponseMapper,
    private val clock: Clock,
    // 추가 연습에서 후보 중 하나를 무작위로 뽑는 데 쓴다. 기본은 Random.Default이고,
    // 테스트에서 시드 고정 Random을 주입해 선택 결과를 결정적으로 검증한다.
    private val random: Random = Random.Default,
) {

    /**
     * 추가 연습('조금 더 풀기')의 다음 문제를 고른다(QA #7 — 같은 문제만 반복하지 않도록 무작위 선정).
     * 사용자별 학습 상태에 따라 우선순위대로 후보를 좁힌 뒤, 후보 중 하나를 애플리케이션 레벨에서 무작위로 뽑는다:
     *  1순위: 아직 풀지 않은 문제.  2순위: 복습 시점이 도래한 개념의 문제.  3순위: 전체 문제(폴백).
     * [userId]가 null이면(실앱의 게스트는 항상 토큰을 보유하므로 예외적 경로) 개인화 없이 전체에서 무작위로 뽑는다.
     * 직전에 제공한 문제와의 즉시 중복은 후보가 여럿일 때 무작위 선정으로 자연히 흩어져, 항상 같은 문제가 반복되지 않는다.
     */
    fun findNextProblem(userId: Long?): NextProblemResponse {
        val problemId = selectNextProblemId(userId)
            ?: throw ProblemNotFoundException.noneAvailable()
        val problem = problemRepository.findById(problemId)
            .orElseThrow { ProblemNotFoundException.byId(problemId) }

        return responseMapper.toNextProblemResponse(problem)
    }

    private fun selectNextProblemId(userId: Long?): Long? {
        val allProblemIds = problemRepository.findAllIdsOrderByIdAsc()
        if (allProblemIds.isEmpty()) return null

        // 무토큰: 개인화할 사용자 컨텍스트가 없으므로 전체에서 무작위(3순위 동작).
        if (userId == null) return allProblemIds.random(random)

        // 1순위: 아직 풀지 않은 문제 중 무작위 1건.
        val solvedProblemIds = sessionRepository.findSolvedProblemIds(userId).toSet()
        val unsolvedProblemIds = allProblemIds.filter { it !in solvedProblemIds }
        if (unsolvedProblemIds.isNotEmpty()) return unsolvedProblemIds.random(random)

        // 2순위: 복습 시점이 도래한 개념의 문제 중 무작위 1건(세션 배정과 같은 선정 로직을 재사용).
        val assignedProblemIds = sessionRepository.findAssignedProblemIds(userId).toSet()
        val dueReviewProblemIds =
            reviewService.selectDueReviewProblemIds(userId, today(), assignedProblemIds, allProblemIds.toSet())
        if (dueReviewProblemIds.isNotEmpty()) return dueReviewProblemIds.random(random)

        // 3순위: 모두 풀었고 도래한 복습도 없으면 전체 풀에서 무작위(반복 허용).
        return allProblemIds.random(random)
    }

    fun submitAnswer(problemId: Long, answer: AnswerText): AttemptResponse {
        val problem = problemRepository.findById(problemId)
            .orElseThrow { ProblemNotFoundException.byId(problemId) }

        val judgement = problem.judge(answer)
        return responseMapper.toAttemptResponse(problem, judgement)
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}
