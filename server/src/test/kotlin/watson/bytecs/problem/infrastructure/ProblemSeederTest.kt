package watson.bytecs.problem.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType

/**
 * 시드 문제 자체가 의도대로 판정되는지 검증한다.
 * 허용답 집합과 유형 태깅은 시드에만 존재하는 데이터라, 도메인 단위 테스트로는 잡히지 않는다.
 */
class ProblemSeederTest {

    private val conceptRepository = mock(ConceptRepository::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val problemSeeder = ProblemSeeder(conceptRepository, problemRepository)

    private companion object {
        const val TIME_COMPLEXITY = "시간 복잡도"
        const val HASH_COLLISION = "해시 충돌"
        const val QUEUE = "큐"
    }

    @Nested
    inner class 시간_복잡도_문제는_유도형이다 {

        @Test
        fun 유형이_유도형으로_태깅된다() {
            val timeComplexityProblem = seededProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.type).isEqualTo(ProblemType.DERIVATION)
        }

        @Test
        fun 제곱_기호만_쓴_표기도_정답이다() {
            val timeComplexityProblem = seededProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.judge(AnswerText("n²"))).isEqualTo(Judgement.CORRECT)
        }

        @Test
        fun 유도를_틀린_답은_근접이_아니라_불일치다() {
            // 이중 반복문을 하나로 잘못 센 전형적인 오답. 편집거리는 1이지만 오타가 아니다.
            val timeComplexityProblem = seededProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.judge(AnswerText("o(n)"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Nested
    inner class 개념_이름을_묻는_문제는_정의_재생형이다 {

        @Test
        fun 유형이_정의_재생형으로_태깅된다() {
            val hashCollisionProblem = seededProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.type).isEqualTo(ProblemType.DEFINITION_RECALL)
        }

        @Test
        fun 개념명의_오타는_근접이다() {
            val hashCollisionProblem = seededProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.judge(AnswerText("collsion"))).isEqualTo(Judgement.NEAR_MISS)
        }

        @Test
        fun 한_글자_허용답에는_근접_판정을_하지_않는다() {
            // "큐"(길이 1)에 "규" — 정의 재생형이어도 근접이 돌면 정답의 길이·모양이 새어 나간다.
            val queueProblem = seededProblemOf(QUEUE)

            assertThat(queueProblem.judge(AnswerText("규"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Test
    fun 이미_문제가_있으면_시딩하지_않는다() {
        // given
        given(problemRepository.count()).willReturn(1L)

        // when
        problemSeeder.run()

        // then
        verifyNoInteractions(conceptRepository)
    }

    /**
     * 시더를 실제로 구동해, 지정한 개념에 대해 시딩된 문제를 꺼낸다.
     * 개념 저장은 인자를 그대로 돌려주는 것으로 대체해, 시드가 만든 [Concept]을 그대로 관찰한다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun seededProblemOf(conceptName: String): Problem {
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        problemSeeder.run()

        val savedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
        verify(problemRepository).saveAll(savedProblems.capture())

        return savedProblems.value.single { it.concept.name == conceptName }
    }
}
