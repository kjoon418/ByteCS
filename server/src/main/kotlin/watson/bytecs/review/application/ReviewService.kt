package watson.bytecs.review.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import java.time.LocalDate

/**
 * 복습·정착(기능 3)의 숙련도 갱신과 복습 문제 선정을 담당한다.
 * 숙련도 산정·간격 공식은 도메인([ConceptMastery]·[MasterySignal])에 위임하고, 서비스는 조회·갱신·선정만 조율한다.
 * 조회가 기본이라 클래스는 읽기 전용 트랜잭션으로 두고, 상태를 바꾸는 갱신만 쓰기 트랜잭션으로 재정의한다.
 */
@Service
@Transactional(readOnly = true)
class ReviewService(
    private val conceptMasteryRepository: ConceptMasteryRepository,
    private val problemRepository: ProblemRepository,
) {

    /**
     * 정답으로 통과한 문제의 **모든** 개념 숙련도를 갱신한다(§3 320~322행). 신규 개념이면 행을 새로 만든다.
     * 세션 제출(SessionService.submitAnswer)의 쓰기 트랜잭션에 합류해, 같은 커밋 경계에서 원자적으로 반영된다(결정적).
     *
     * [alreadySolved] — 이 문제를 (이번 정답 이전에) 이미 한 번이라도 정답으로 통과한 적이 있는지(D8).
     * 세션 소진 시 반복 폴백(도메인 §1.5)으로 **아직 복습 시점이 도래하지 않은** 문제를 재출제해 다시 맞힌 경우엔,
     * 그 정답으로 개념 숙련도·다음 복습 시점을 갱신하지 않는다 — 재출제 정답이 복습 도래 전 숙련도를 부풀려
     * 간격 반복을 교란하지 않도록 하기 위함이다. 복습 시점이 도래한 뒤의 정상 재출제(진짜 복습)는 그대로 갱신한다.
     */
    @Transactional
    fun recordSolve(
        userId: Long,
        conceptIds: List<Long>,
        signal: MasterySignal,
        solvedOn: LocalDate,
        problemId: Long,
        alreadySolved: Boolean,
    ) {
        conceptIds.forEach { conceptId ->
            val mastery = conceptMasteryRepository.findByUserIdAndConceptId(userId, conceptId)
            if (mastery == null) {
                conceptMasteryRepository.save(
                    ConceptMastery.firstSolve(userId, conceptId, signal, solvedOn, problemId),
                )
            } else if (!(alreadySolved && mastery.nextReviewDate.isAfter(solvedOn))) {
                mastery.applySolve(signal, solvedOn, problemId)
            }
        }
    }

    /**
     * 복습 시점이 도래한 개념들의 복습 문제 id를, 도래 우선·개념 id 순으로 돌려준다(중복은 선착순 제거).
     *  - 기본: 그 개념을 마지막으로 갱신한 문제([ConceptMastery.lastProblemId]) = '그때 푼 그 문제'를 재출제.
     *  - 예외(유도형): lastProblemId가 유도형이고, 그 개념에 이 사용자에게 **아직 배정된 적 없는** 문제가 있으면
     *    그중 최소 id를 우선한다(문항–답 연합 회피). 없으면 같은 문제로 낸다.
     *  - 정의 재생형엔 예외가 없다(다른 문제를 내도 정답이 같은 개념 이름이라 연합을 막지 못한다).
     *  - 후보가 현재 풀([poolIds])에 없으면(회수·삭제) 그 복습은 건너뛴다(세션이 죽지 않게).
     */
    fun selectDueReviewProblemIds(
        userId: Long,
        today: LocalDate,
        assignedProblemIds: Set<Long>,
        poolIds: Set<Long>,
    ): List<Long> {
        val dueMasteries = conceptMasteryRepository
            .findByUserIdAndNextReviewDateLessThanEqualOrderByNextReviewDateAscConceptIdAsc(userId, today)
        return dueMasteries
            .mapNotNull { pickReviewProblemId(it, assignedProblemIds, poolIds) }
            .distinct()
    }

    private fun pickReviewProblemId(
        mastery: ConceptMastery,
        assignedProblemIds: Set<Long>,
        poolIds: Set<Long>,
    ): Long? {
        if (problemRepository.findTypeById(mastery.lastProblemId) == ProblemType.DERIVATION) {
            val unassigned = problemRepository.findApprovedIdsByConceptIdOrderByIdAsc(mastery.conceptId)
                .firstOrNull { it !in assignedProblemIds && it in poolIds }
            if (unassigned != null) {
                return unassigned
            }
        }
        // 기본 재출제(또는 유도형이지만 안 낸 문제가 없을 때). 회수돼 풀에 없으면 이 복습은 건너뛴다.
        return mastery.lastProblemId.takeIf { it in poolIds }
    }
}
