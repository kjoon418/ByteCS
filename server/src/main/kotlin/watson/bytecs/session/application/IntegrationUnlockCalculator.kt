package watson.bytecs.session.application

import org.springframework.stereotype.Component
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.application.dto.UnlockedIntegrationResponse
import watson.bytecs.session.domain.Session
import watson.bytecs.study.LearningHistory

/**
 * 세션 완료 시, 그 세션으로 **새로 열린** 지정 연결 문제(integration=true)를 계산하는 협력자(계획 §3.2 · D2, DI12).
 *
 * '새로 열림' = 이 세션에서 **처음 만난 개념**이 마지막 조각이 되어, 그 문제의 전 구성 개념이 학습 상태가 된 연결 문제다.
 * 상태 테이블을 추가하지 않고 학습 이력에서 결정적으로 재계산한다(같은 이력 → 같은 결과):
 *  - newConcepts = (이 세션에서 푼 문제의 개념) − (다른 세션에서 푼 문제의 개념) → 이 세션에서 처음 만난 개념.
 *    복습·D8 재출제로 이 세션에 다시 나온 문제의 개념은 다른 세션에서 이미 풀었으므로 newConcepts에서 빠진다(중복 축하 방지의 핵심).
 *  - 연결 문제 P가 새로 열림 = P의 모든 개념이 학습됨(masteredNow) ∧ P의 개념 중 하나 이상이 newConcepts.
 *    → 이미 열려 있던 문제(newConcepts와 교집합 없음)는 자연히 제외된다.
 * 성능: 지정 연결 문제 전량(소수 큐레이션)·푼 문제 개념 매핑·보유 개념을 각각 1회 조회해 메모리에서 접는다(N+1 없음).
 * 게스트도 동일하게 계산한다(userId 기준).
 */
@Component
class IntegrationUnlockCalculator(
    private val problemRepository: ProblemRepository,
    private val reviewService: ReviewService,
    private val learningHistory: LearningHistory,
) {

    fun calculate(userId: Long, completedSession: Session): List<UnlockedIntegrationResponse> {
        val integrationProblems = problemRepository.findApprovedIntegrationProblemsWithConcepts()
        if (integrationProblems.isEmpty()) {
            return emptyList()
        }

        val sessionProblemIds = completedSession.items.map { it.problemId }
        val otherSolvedProblemIds = learningHistory.findSolvedProblemIdsExcept(userId, completedSession.id)
        val conceptIdsByProblem = problemRepository
            .findConceptIdsByProblemIdIn(sessionProblemIds + otherSolvedProblemIds)
            .groupBy({ it.problemId }, { it.conceptId })

        val thisSessionConcepts = sessionProblemIds.flatMap { conceptIdsByProblem[it].orEmpty() }.toSet()
        val priorConcepts = otherSolvedProblemIds.flatMap { conceptIdsByProblem[it].orEmpty() }.toSet()
        val newConcepts = thisSessionConcepts - priorConcepts
        if (newConcepts.isEmpty()) {
            // 이 세션에서 처음 만난 개념이 없다(전부 복습·재출제) → 새로 열린 연결 문제도 없다.
            return emptyList()
        }

        val masteredConceptIds = reviewService.findMasteredConceptIds(userId)
        return integrationProblems
            .filter { problem ->
                val conceptIds = problem.conceptIds()
                masteredConceptIds.containsAll(conceptIds) && conceptIds.any { it in newConcepts }
            }
            .map { UnlockedIntegrationResponse(concepts = it.conceptNames()) }
    }
}
