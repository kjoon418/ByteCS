package watson.bytecs.review.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import java.time.LocalDate

/**
 * 복습 서비스 슬라이스 테스트.
 * 레포지토리는 Mockito로 대체하고, 숙련도 갱신(신규/기존)과 복습 문제 선정(기본·유도형 예외·회수 처리)을 검증한다.
 */
class ReviewServiceTest {

    private val conceptMasteryRepository = mock(ConceptMasteryRepository::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val reviewService = ReviewService(conceptMasteryRepository, problemRepository)

    private companion object {
        const val USER_ID = 1L
        const val OTHER_USER_ID = 2L
        const val CONCEPT_A = 10L
        const val CONCEPT_B = 20L
        val TODAY: LocalDate = LocalDate.of(2026, 7, 14)
    }

    @Nested
    inner class 숙련도를_갱신한다 {

        @Test
        fun 신규_개념이면_첫_통과로_행을_만든다() {
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(null)

            reviewService.recordSolve(
                USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY, problemId = 100L, alreadySolved = false,
            )

            val saved = ArgumentCaptor.forClass(ConceptMastery::class.java)
            verify(conceptMasteryRepository).save(saved.capture())
            assertThat(saved.value.userId).isEqualTo(USER_ID)
            assertThat(saved.value.conceptId).isEqualTo(CONCEPT_A)
            assertThat(saved.value.level).isEqualTo(1)
            assertThat(saved.value.lastProblemId).isEqualTo(100L)
        }

        @Test
        fun 기존_개념이면_행을_갱신하고_다시_저장하지_않는다() {
            // 관리 상태 엔티티는 더티 체킹으로 반영되므로, 명시적 save 없이 상태만 바뀌어야 한다.
            val existing = ConceptMastery.firstSolve(USER_ID, CONCEPT_A, MasterySignal.AIDED, TODAY, problemId = 100L)
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(existing)

            reviewService.recordSolve(
                USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY.plusDays(1), problemId = 200L, alreadySolved = false,
            )

            assertThat(existing.level).isEqualTo(1) // 0 → 1
            assertThat(existing.lastProblemId).isEqualTo(200L)
            verify(conceptMasteryRepository, never()).save(existing)
        }

        @Test
        fun 문제의_모든_개념에_적용한다() {
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(null)
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_B)).willReturn(null)

            reviewService.recordSolve(
                USER_ID, listOf(CONCEPT_A, CONCEPT_B), MasterySignal.UNAIDED, TODAY, problemId = 100L, alreadySolved = false,
            )

            verify(conceptMasteryRepository, times(2)).save(org.mockito.ArgumentMatchers.any())
        }

        @Test
        fun 사용자별로_격리해_조회한다() {
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(null)

            reviewService.recordSolve(
                USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY, problemId = 100L, alreadySolved = false,
            )

            verify(conceptMasteryRepository).findByUserIdAndConceptId(USER_ID, CONCEPT_A)
            verify(conceptMasteryRepository, never()).findByUserIdAndConceptId(OTHER_USER_ID, CONCEPT_A)
        }
    }

    /**
     * D8: 세션 소진 시 반복 폴백(도메인 §1.5)으로 이미 푼 문제를 재출제해 다시 맞혀도,
     * 복습 시점이 도래하기 전이면 그 정답으로 숙련도·다음 복습 시점을 갱신하지 않는다.
     * 도래한 뒤의 재출제(정상 복습)나 신규 정답은 지금과 동일하게 갱신한다.
     */
    @Nested
    inner class 반복_재정답의_숙련도_갱신을_제외한다 {

        @Test
        fun 복습_미도래_상태에서_재출제된_문제를_다시_맞히면_갱신을_건너뛴다() {
            // 마지막 정답이 TODAY, 사다리[1]=3일 뒤(TODAY+3)가 다음 복습 — 아직 도래 전이다.
            val existing = ConceptMastery.firstSolve(USER_ID, CONCEPT_A, MasterySignal.UNAIDED, TODAY, problemId = 100L)
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(existing)

            reviewService.recordSolve(
                USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY.plusDays(1),
                problemId = 100L, alreadySolved = true,
            )

            assertThat(existing.level).isEqualTo(1) // 그대로 — 갱신되지 않았다.
            assertThat(existing.nextReviewDate).isEqualTo(TODAY.plusDays(3)) // 그대로.
            assertThat(existing.lastProblemId).isEqualTo(100L) // 그대로.
        }

        @Test
        fun 복습_시점이_도래한_뒤의_재출제_정답은_정상_갱신한다() {
            // 다음 복습이 TODAY+3인데, TODAY+3 당일에 다시 맞혔다 — 도래한 정상 복습이다.
            val existing = ConceptMastery.firstSolve(USER_ID, CONCEPT_A, MasterySignal.UNAIDED, TODAY, problemId = 100L)
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(existing)

            reviewService.recordSolve(
                USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY.plusDays(3),
                problemId = 100L, alreadySolved = true,
            )

            assertThat(existing.level).isEqualTo(2) // 1 → 2, 정상 갱신됐다.
            assertThat(existing.nextReviewDate).isEqualTo(TODAY.plusDays(3).plusDays(7))
        }

        @Test
        fun 처음_보는_정답이면_alreadySolved가_아니어도_정상_갱신한다() {
            val existing = ConceptMastery.firstSolve(USER_ID, CONCEPT_A, MasterySignal.UNAIDED, TODAY, problemId = 100L)
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(existing)

            reviewService.recordSolve(
                USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY.plusDays(1),
                problemId = 200L, alreadySolved = false,
            )

            assertThat(existing.level).isEqualTo(2) // 정상 갱신됐다.
            assertThat(existing.lastProblemId).isEqualTo(200L)
        }
    }

    @Nested
    inner class 복습_문제를_선정한다 {

        @Test
        fun 기본은_그때_푼_그_문제를_재출제한다() {
            givenDue(masteryOf(CONCEPT_A, lastProblemId = 100L))
            given(problemRepository.findTypeById(100L)).willReturn(ProblemType.DEFINITION_RECALL)

            val result = reviewService.selectDueReviewProblemIds(
                USER_ID, TODAY, assignedProblemIds = setOf(100L), poolIds = setOf(100L),
            )

            assertThat(result).containsExactly(100L)
        }

        @Test
        fun 유도형이면_아직_안_낸_다른_문제를_우선한다() {
            givenDue(masteryOf(CONCEPT_A, lastProblemId = 100L))
            given(problemRepository.findTypeById(100L)).willReturn(ProblemType.DERIVATION)
            // 개념 A의 문제 풀: 100(이미 냄), 105·110(아직 안 냄). 최소 미배정 id는 105.
            given(problemRepository.findApprovedIdsByConceptIdOrderByIdAsc(CONCEPT_A)).willReturn(listOf(100L, 105L, 110L))

            val result = reviewService.selectDueReviewProblemIds(
                USER_ID, TODAY, assignedProblemIds = setOf(100L), poolIds = setOf(100L, 105L, 110L),
            )

            assertThat(result).containsExactly(105L)
        }

        @Test
        fun 유도형이지만_안_낸_문제가_없으면_같은_문제로_낸다() {
            givenDue(masteryOf(CONCEPT_A, lastProblemId = 100L))
            given(problemRepository.findTypeById(100L)).willReturn(ProblemType.DERIVATION)
            // 개념 A의 문제가 100 하나뿐이고 이미 배정됨 → 미배정 후보 없음 → 같은 문제.
            given(problemRepository.findApprovedIdsByConceptIdOrderByIdAsc(CONCEPT_A)).willReturn(listOf(100L))

            val result = reviewService.selectDueReviewProblemIds(
                USER_ID, TODAY, assignedProblemIds = setOf(100L), poolIds = setOf(100L),
            )

            assertThat(result).containsExactly(100L)
        }

        @Test
        fun 복습_후보가_회수돼_풀에_없으면_건너뛴다() {
            givenDue(masteryOf(CONCEPT_A, lastProblemId = 100L))
            given(problemRepository.findTypeById(100L)).willReturn(ProblemType.DEFINITION_RECALL)

            val result = reviewService.selectDueReviewProblemIds(
                USER_ID, TODAY, assignedProblemIds = setOf(100L), poolIds = emptySet(),
            )

            assertThat(result).isEmpty()
        }

        @Test
        fun 중복_문제는_선착순으로_제거한다() {
            // 두 개념이 같은 문제를 마지막으로 풀었으면(복수 개념 문제), 한 번만 편입한다.
            givenDue(
                masteryOf(CONCEPT_A, lastProblemId = 100L),
                masteryOf(CONCEPT_B, lastProblemId = 100L),
            )
            given(problemRepository.findTypeById(100L)).willReturn(ProblemType.DEFINITION_RECALL)

            val result = reviewService.selectDueReviewProblemIds(
                USER_ID, TODAY, assignedProblemIds = setOf(100L), poolIds = setOf(100L),
            )

            assertThat(result).containsExactly(100L)
        }
    }

    private fun masteryOf(conceptId: Long, lastProblemId: Long): ConceptMastery =
        ConceptMastery.firstSolve(USER_ID, conceptId, MasterySignal.UNAIDED, TODAY, lastProblemId)

    private fun givenDue(vararg masteries: ConceptMastery) {
        given(
            conceptMasteryRepository
                .findByUserIdAndNextReviewDateLessThanEqualOrderByNextReviewDateAscConceptIdAsc(USER_ID, TODAY),
        ).willReturn(masteries.toList())
    }
}
