package watson.bytecs.problem.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Enrichment
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType

/**
 * 시드 JSON(`problems-generated.json`)이 로더를 거쳐 의도대로 조립·판정되는지 검증한다.
 * 허용답 집합과 유형 태깅은 시드에만 존재하는 데이터라, 도메인 단위 테스트로는 잡히지 않는다.
 *
 * 로더는 곧 검증기다 — 잘못된 시드 데이터(필수 필드 누락·불변식 위반)는 조용히 스킵되지 않고
 * 예외로 기동을 실패시켜야 한다([잘못된_시드_데이터는_조용히_스킵되지_않는다]).
 */
class ProblemDataLoaderTest {

    private val conceptRepository = mock(ConceptRepository::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val problemDataLoader = ProblemDataLoader(conceptRepository, problemRepository, jacksonObjectMapper())

    private companion object {
        const val TIME_COMPLEXITY = "시간 복잡도"
        const val HASH_COLLISION = "해시 충돌"
        const val QUEUE = "큐"
        const val STACK = "스택"
        const val PROCESS_AND_THREAD = "프로세스와 스레드"
    }

    @Nested
    inner class 시간_복잡도_문제는_유도형이다 {

        @Test
        fun 유형이_유도형으로_태깅된다() {
            val timeComplexityProblem = loadedProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.type).isEqualTo(ProblemType.DERIVATION)
        }

        @Test
        fun 제곱_기호만_쓴_표기도_정답이다() {
            val timeComplexityProblem = loadedProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.judge(AnswerText("n²"))).isEqualTo(Judgement.CORRECT)
        }

        @Test
        fun 유도를_틀린_답은_근접이_아니라_불일치다() {
            // 이중 반복문을 하나로 잘못 센 전형적인 오답. 편집거리는 1이지만 오타가 아니다.
            val timeComplexityProblem = loadedProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.judge(AnswerText("o(n)"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Nested
    inner class 개념_이름을_묻는_문제는_정의_재생형이다 {

        @Test
        fun 유형이_정의_재생형으로_태깅된다() {
            val hashCollisionProblem = loadedProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.type).isEqualTo(ProblemType.DEFINITION_RECALL)
        }

        @Test
        fun 개념명의_오타는_근접이다() {
            val hashCollisionProblem = loadedProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.judge(AnswerText("collsion"))).isEqualTo(Judgement.NEAR_MISS)
        }

        @Test
        fun 한_글자_허용답에는_근접_판정을_하지_않는다() {
            // "큐"(길이 1)에 "규" — 정의 재생형이어도 근접이 돌면 정답의 길이·모양이 새어 나간다.
            val queueProblem = loadedProblemOf(QUEUE)

            assertThat(queueProblem.judge(AnswerText("규"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Nested
    inner class 힌트를_로드한다 {

        @Test
        fun 힌트가_0개인_문제를_최소_하나_남긴다() {
            // 진입점 미노출 분기(hintCount==0)가 시드로도 실행되게 하려면 힌트 0개 문제가 반드시 있어야 한다.
            val problems = allLoadedProblems()

            assertThat(problems.any { it.hintCount == 0 }).isTrue()
        }

        @Test
        fun 힌트가_있는_문제는_약에서_강_순서로_담긴다() {
            val hashCollisionProblem = loadedProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.hintCount).isGreaterThanOrEqualTo(2)
            // 앞에서 자를수록 부분집합이어야 한다(순서 보존).
            val firstOne = hashCollisionProblem.revealedHints(1).map { it.text }
            val firstTwo = hashCollisionProblem.revealedHints(2).map { it.text }
            assertThat(firstTwo).startsWith(*firstOne.toTypedArray())
            assertThat(firstTwo).hasSize(2)
        }

        @Test
        fun 어떤_힌트도_해설이_아니라_정답을_노출하지_않는다() {
            // 콘텐츠 신뢰성 가드레일: 힌트·교정 메시지에 허용답 문자열이 섞이면 안 된다.
            val problems = allLoadedProblems()

            problems.forEach { problem ->
                val answers = problem.acceptableAnswers.map { AnswerText(it).value }
                val texts = problem.revealedHints(problem.hintCount).map { it.text } +
                    problem.misconceptionHints.map { it.message }
                texts.forEach { text ->
                    val normalized = text.lowercase()
                    answers.forEach { answer ->
                        assertThat(normalized)
                            .`as`("힌트/교정 메시지가 정답 '%s'을(를) 노출하면 안 된다: %s", answer, text)
                            .doesNotContain(answer)
                    }
                }
            }
        }
    }

    @Nested
    inner class 심화_정보를_로드한다 {

        @Test
        fun 해시_충돌_문제는_심화_정보를_가진다() {
            val hashCollisionProblem = loadedProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.enrichment).isNotNull()
        }

        @Test
        fun 스레드_문제는_심화_정보를_가진다() {
            val threadProblem = loadedProblemOf(PROCESS_AND_THREAD)

            assertThat(threadProblem.enrichment).isNotNull()
        }

        @Test
        fun 해시_충돌_심화_정보는_시안_구조를_순서대로_따른다() {
            // 질문형 제목 + 리드 문단 + 해결책 항목(순서 보존) + 인용의 시안 원형.
            val enrichment = loadedProblemOf(HASH_COLLISION).enrichment!!

            assertThat(enrichment.title).isEqualTo("왜 충돌이 발생할까요?")
            assertThat(enrichment.body).isNotBlank()
            assertThat(enrichment.items.map { it.title })
                .containsExactly("해결책 01. 체이닝", "해결책 02. 개방 주소법")
            assertThat(enrichment.items.all { it.description.isNotBlank() }).isTrue()
            assertThat(enrichment.quote).isNotBlank()
        }

        @Test
        fun 심화_정보가_없는_문제를_최소_하나_남긴다() {
            // graceful 분기(없으면 그냥 다음으로)가 시드로도 실행되게 하려면 심화 정보 없는 문제가 반드시 있어야 한다.
            val problems = allLoadedProblems()

            assertThat(problems.any { it.enrichment == null }).isTrue()
        }

        @Test
        fun 어떤_심화_정보도_다른_문제의_정답을_새로_노출하지_않는다() {
            // 콘텐츠 신뢰성 가드레일: 심화 정보는 자기 문제의 정답(이미 맞힌 것)은 언급해도 되지만,
            // 아직 안 풀었을 수 있는 '다른' 문제의 허용답 문자열을 새로 흘리면 안 된다.
            val problems = allLoadedProblems()

            problems.forEach { problem ->
                val enrichment = problem.enrichment ?: return@forEach
                // 구조 필드 전체(title·body·items의 제목/설명·quote)를 한 문자열로 훑는다.
                val allText = enrichmentText(enrichment)
                val normalized = allText.lowercase()
                val otherAnswers = problems.filter { it !== problem }
                    .flatMap { it.acceptableAnswers.map { answer -> AnswerText(answer).value } }
                otherAnswers.forEach { answer ->
                    assertThat(normalized)
                        .`as`("심화 정보가 다른 문제의 정답 '%s'을(를) 노출하면 안 된다: %s", answer, allText)
                        .doesNotContain(answer)
                }
            }
        }

        /** 심화 정보의 모든 텍스트 필드를 한 문자열로 모은다(no-leak 스윕이 구조 전체를 훑도록). */
        private fun enrichmentText(enrichment: Enrichment): String =
            buildList {
                add(enrichment.title)
                add(enrichment.body)
                enrichment.items.forEach {
                    add(it.title)
                    add(it.description)
                }
                enrichment.quote?.let { add(it) }
            }.joinToString(" ")
    }

    @Nested
    inner class 대표_정답을_로드한다 {

        @Test
        fun 모든_문제가_대표_정답_불변식을_만족한다() {
            // 화면의 대표 정답을 그대로 따라 입력하면 통과해야 하므로, 정규화 기준으로 허용답에 있어야 한다.
            // (도메인 Problem의 init require가 로드 시점에 이미 강제하지만, 여기서도 명시적으로 확인한다.)
            val problems = allLoadedProblems()

            problems.forEach { problem ->
                assertThat(problem.representativeAnswer).isNotBlank()
                val normalizedAnswers = problem.acceptableAnswers.map { AnswerText(it).value }
                assertThat(AnswerText(problem.representativeAnswer).value)
                    .`as`("대표 정답 '%s'은(는) 정규화 기준으로 허용답에 있어야 한다", problem.representativeAnswer)
                    .isIn(normalizedAnswers)
            }
        }

        @Test
        fun 한영_병기가_자연스러운_문제는_병기_표기를_대표로_둔다() {
            val threadProblem = loadedProblemOf(PROCESS_AND_THREAD)

            assertThat(threadProblem.representativeAnswer).isEqualTo("스레드 (thread)")
        }

        @Test
        fun 유도형은_표준_수식_표기를_대표로_둔다() {
            val timeComplexityProblem = loadedProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.representativeAnswer).isEqualTo("O(n²)")
        }
    }

    @Nested
    inner class 오답_교정_힌트를_로드한다 {

        @Test
        fun 스레드_문제는_프로세스_오답에_교정_힌트를_준다() {
            val threadProblem = loadedProblemOf(PROCESS_AND_THREAD)

            val outcome = threadProblem.evaluate(AnswerText("프로세스"))

            assertThat(outcome.judgement).isEqualTo(Judgement.MISMATCH)
            assertThat(outcome.misconceptionHint).isNotNull()
        }
    }

    @Nested
    inner class 복수_개념을_태깅한다 {

        @Test
        fun 최소_한_문제는_복수_개념으로_태깅된다() {
            // 문제 N—M 개념 경로가 시드로도 실행되게 하려면 복수 개념 문제가 반드시 있어야 한다.
            val problems = allLoadedProblems()

            assertThat(problems.any { it.conceptNames().size >= 2 }).isTrue()
        }

        @Test
        fun 스레드_문제는_대표_개념을_앞에_둔_복수_개념을_가진다() {
            // 태깅 순서는 대표 개념(프로세스와 스레드)이 먼저, 그다음 각자 갖는 자원인 스택.
            val threadProblem = loadedProblemOf(PROCESS_AND_THREAD)

            assertThat(threadProblem.conceptNames()).containsExactly(PROCESS_AND_THREAD, STACK)
        }
    }

    @Nested
    inner class 개념을_이름_기준으로_공유한다 {

        @Test
        fun 문제_간에_같은_이름의_개념은_같은_행을_공유한다() {
            // "스택"은 스레드 문제와 스택 문제 양쪽에 태깅된다. 로더 안에서 같은 이름은 한 번만 생성돼야 한다.
            given(problemRepository.count()).willReturn(0L)
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            problemDataLoader.run()

            // 시드 JSON의 고유 개념 이름은 7개(프로세스와 스레드·스택·큐·해시 충돌·TCP·캐시·시간 복잡도) —
            // "스택"이 두 문제에 걸쳐 재사용되므로, 개념 저장 횟수는 문제 태깅 총량이 아니라 고유 이름 수와 같아야 한다.
            verify(conceptRepository, times(7)).save(any(Concept::class.java))
        }

        @Test
        @Suppress("UNCHECKED_CAST")
        fun DB에_이미_있는_개념은_새로_만들지_않는다() {
            given(problemRepository.count()).willReturn(0L)
            val existingStack = Concept("스택")
            given(conceptRepository.findByName(anyString())).willAnswer { invocation ->
                if (invocation.getArgument<String>(0) == "스택") existingStack else null
            }
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            problemDataLoader.run()

            val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
            verify(problemRepository).saveAll(loadedProblems.capture())
            val stackProblem = loadedProblems.value.single { it.conceptNames().contains(STACK) && it.conceptNames().size == 1 }
            assertThat(stackProblem.concepts).contains(existingStack)
            // "스택"은 이미 존재하므로 새로 save 되어서는 안 된다.
            verify(conceptRepository, org.mockito.Mockito.never()).save(existingStack)
        }
    }

    @Test
    fun 이미_문제가_있으면_로드하지_않는다() {
        // given
        given(problemRepository.count()).willReturn(1L)

        // when
        problemDataLoader.run()

        // then
        verifyNoInteractions(conceptRepository)
    }

    @Test
    fun 잘못된_시드_데이터는_조용히_스킵되지_않는다() {
        // 대표 정답이 허용답 집합에 없는 불량 데이터(server/src/test/resources/seed/invalid-problems.json).
        // 도메인 Problem의 init require가 발동해, 조용한 스킵이 아니라 예외로 기동이 실패해야 한다.
        val invalidLoader = ProblemDataLoader(
            conceptRepository,
            problemRepository,
            jacksonObjectMapper(),
            resourcePath = "seed/invalid-problems.json",
        )
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willReturn(null)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        assertThatThrownBy { invalidLoader.run() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    /** 로더를 실제로 구동해, 로드된 전체 문제를 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun allLoadedProblems(): List<Problem> {
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willReturn(null)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        problemDataLoader.run()

        val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
        verify(problemRepository).saveAll(loadedProblems.capture())
        return loadedProblems.value
    }

    /**
     * 로더를 실제로 구동해, 지정한 개념에 대해 로드된 문제를 꺼낸다.
     * 개념 저장은 인자를 그대로 돌려주는 것으로 대체해, 로더가 만든 [Concept]을 그대로 관찰한다.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadedProblemOf(conceptName: String): Problem {
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willReturn(null)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        problemDataLoader.run()

        val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
        verify(problemRepository).saveAll(loadedProblems.capture())

        return loadedProblems.value.single { it.conceptNames().contains(conceptName) }
    }
}
