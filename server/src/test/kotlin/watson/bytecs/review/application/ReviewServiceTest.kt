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

            reviewService.recordSolve(USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY, problemId = 100L)

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

            reviewService.recordSolve(USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY.plusDays(1), problemId = 200L)

            assertThat(existing.level).isEqualTo(1) // 0 → 1
            assertThat(existing.lastProblemId).isEqualTo(200L)
            verify(conceptMasteryRepository, never()).save(existing)
        }

        @Test
        fun 문제의_모든_개념에_적용한다() {
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(null)
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_B)).willReturn(null)

            reviewService.recordSolve(USER_ID, listOf(CONCEPT_A, CONCEPT_B), MasterySignal.UNAIDED, TODAY, problemId = 100L)

            verify(conceptMasteryRepository, times(2)).save(org.mockito.ArgumentMatchers.any())
        }

        @Test
        fun 사용자별로_격리해_조회한다() {
            given(conceptMasteryRepository.findByUserIdAndConceptId(USER_ID, CONCEPT_A)).willReturn(null)

            reviewService.recordSolve(USER_ID, listOf(CONCEPT_A), MasterySignal.UNAIDED, TODAY, problemId = 100L)

            verify(conceptMasteryRepository).findByUserIdAndConceptId(USER_ID, CONCEPT_A)
            verify(conceptMasteryRepository, never()).findByUserIdAndConceptId(OTHER_USER_ID, CONCEPT_A)
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
            given(problemRepository.findIdsByConceptIdOrderByIdAsc(CONCEPT_A)).willReturn(listOf(100L, 105L, 110L))

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
            given(problemRepository.findIdsByConceptIdOrderByIdAsc(CONCEPT_A)).willReturn(listOf(100L))

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
