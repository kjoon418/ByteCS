package watson.bytecs.interview.application

import org.springframework.stereotype.Component
import watson.bytecs.interview.domain.InterviewEligibility
import watson.bytecs.interview.infrastructure.InterviewPromptRepository
import watson.bytecs.review.infrastructure.ConceptMasteryRepository

/**
 * 주관식 정답으로 개념이 **처음 면접 후보가 되는 순간**을 계산하는 협력자(계획 §3.3 · DI9).
 * [watson.bytecs.session.application.IntegrationUnlockCalculator]와 같은 결이되, 세션 완료가 아니라 **정답 하나마다** 돈다 —
 * 면접 후보 승급은 개념이 숙련도 레벨 <임계 → ≥임계로 오르는 그 순간의 사건이라, 완료까지 미루면 '바로 알림'이 안 되기 때문이다.
 *
 * '새로 열림' 판정은 recordSolve 전후 스냅샷의 차이로 결정적으로 낸다(상태 테이블 추가 없음):
 *  1. [eligibleConceptIdsBefore] — recordSolve **전** 이미 후보였던(레벨≥임계) 개념. 이번 문제의 개념만 대상.
 *  2. [newlyUnlockedConceptNames] — recordSolve **후** 후보가 됐고 [eligibleConceptIdsBefore]엔 없던 개념 중,
 *     **승인된 면접 질문이 실제 있는** 것만(질문 없으면 헛약속이라 제외 — 홈 후보 정의와 동일 기준).
 * 후보 판정은 면접 세션 후보 산정과 **같은 쿼리·같은 임계**([InterviewEligibility.MASTERY_LEVEL])를 써, 두 경로가 갈리지 않게 한다.
 *
 * ⚠️ 반드시 [eligibleConceptIdsBefore]를 recordSolve **전에** 부르고 [newlyUnlockedConceptNames]를 그 **후에** 불러야 한다 —
 * recordSolve가 같은 트랜잭션에서 레벨을 올리므로, 순서가 뒤집히면 이미 오른 레벨을 '전'으로 읽어 승급을 놓친다.
 */
@Component
class InterviewUnlockCalculator(
    private val conceptMasteryRepository: ConceptMasteryRepository,
    private val interviewPromptRepository: InterviewPromptRepository,
) {

    /**
     * recordSolve **전** 시점에, 주어진 개념들 중 이미 면접 후보(레벨≥임계)인 것을 스냅샷한다.
     * 이 집합을 [newlyUnlockedConceptNames]에 넘겨 '이미 열려 있던 개념을 다시 맞힌' 경우를 제외한다(중복 알림 방지, DI9).
     */
    fun eligibleConceptIdsBefore(userId: Long, conceptIds: List<Long>): Set<Long> {
        if (conceptIds.isEmpty()) {
            return emptySet()
        }
        val eligible = eligibleConceptIds(userId)
        return conceptIds.filterTo(mutableSetOf()) { it in eligible }
    }

    /**
     * recordSolve **후** 시점 기준, 이번 정답으로 **새로** 열린 면접 후보 개념명을 태깅 순서대로 돌려준다.
     * '새로 열림' = 후보가 됐고([eligibleBefore]에 없음) 그 개념에 승인된 면접 질문이 있는 개념. 없으면 빈 목록.
     */
    fun newlyUnlockedConceptNames(
        userId: Long,
        conceptIds: List<Long>,
        eligibleBefore: Set<Long>,
    ): List<String> {
        if (conceptIds.isEmpty()) {
            return emptyList()
        }
        val eligibleNow = eligibleConceptIds(userId)
        val newlyEligible = conceptIds.filter { it !in eligibleBefore && it in eligibleNow }
        if (newlyEligible.isEmpty()) {
            return emptyList()
        }

        val nameByConceptId = interviewPromptRepository.findApprovedByConceptIdIn(newlyEligible)
            .associateBy({ it.concept.id }, { it.concept.name })
        // 승인 질문이 있는 개념만 남긴다(질문 없으면 '면접 문제가 생겼다'가 헛약속). 입력 순서(태깅 순)를 보존한다.
        return newlyEligible.mapNotNull { nameByConceptId[it] }
    }

    /** 그 사용자의 현재 면접 후보 개념 id(레벨≥임계). 면접 세션 후보 산정과 같은 쿼리·임계를 쓴다. */
    private fun eligibleConceptIds(userId: Long): Set<Long> =
        conceptMasteryRepository
            .findConceptIdsByUserIdAndLevelGreaterThanEqual(userId, InterviewEligibility.MASTERY_LEVEL)
            .toSet()
}
