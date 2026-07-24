package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.infrastructure.ConceptRepository

/**
 * 면접 질문 시드 JSON이 로더를 거쳐 의도대로 조립·검증되는지 검증한다(Problem 시드 로더 관례와 동일).
 * 로더는 곧 검증기다 — 참조 개념이 없거나 불변식을 어긴 시드는 조용히 스킵되지 않고 예외로 기동을 실패시킨다.
 */
class InterviewPromptDataLoaderTest {

    private val conceptRepository = mock(ConceptRepository::class.java)
    private val interviewPromptRepository = mock(InterviewPromptRepository::class.java)

    private fun loader(resourcePath: String) =
        InterviewPromptDataLoader(conceptRepository, interviewPromptRepository, jacksonObjectMapper(), resourcePath)

    @Test
    fun `정상 시드를 도메인으로 조립해 승인 상태로 저장한다`() {
        given(interviewPromptRepository.count()).willReturn(0L)
        given(conceptRepository.findByName("스택")).willReturn(Concept("스택"))

        val saved = runAndCapture("seed/interview-prompts-fixture.json")

        assertThat(saved).hasSize(1)
        val prompt = saved.single()
        assertThat(prompt.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
        assertThat(prompt.concept.name).isEqualTo("스택")
        assertThat(prompt.rubricPoints).containsExactly("스택은 LIFO임을 언급", "큐는 FIFO임을 언급")
        // 힌트 파싱 커버리지: 픽스처에 실은 약→강 순서가 그대로 보존되어야 한다.
        assertThat(prompt.hints).containsExactly(
            "먼저 넣은 게 먼저 나오는 구조와, 나중에 넣은 게 먼저 나오는 구조를 비교해보세요.",
            "접시 쌓기와 줄 서기를 떠올려보세요.",
        )
    }

    @Test
    fun `hints 필드가 없는 시드도 하위 호환으로 로드된다(힌트 0개)`() {
        given(interviewPromptRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willAnswer { invocation ->
            Concept(invocation.getArgument(0))
        }

        // JSON에 hints 키가 없어도 기동이 실패하면 안 된다 — 운영 시드는 이제 힌트를 채웠으므로 전용 픽스처로 검증한다.
        val saved = runAndCapture("seed/interview-prompts-no-hints-fixture.json")

        assertThat(saved).isNotEmpty
        assertThat(saved).allMatch { it.hintCount == 0 }
    }

    @Test
    fun `운영 시드는 전 문항에 점진 공개 힌트 2개를 싣는다`() {
        given(interviewPromptRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willAnswer { invocation ->
            Concept(invocation.getArgument(0))
        }

        val saved = runAndCapture("seed/interview-prompts.json")

        assertThat(saved).isNotEmpty
        assertThat(saved).allMatch { it.hintCount == 2 }
    }

    @Test
    fun `빈 시드는 예외 없이 기동한다(graceful)`() {
        given(interviewPromptRepository.count()).willReturn(0L)

        // 빈 파일이어도 정상 기동해야 한다 — 운영 기본 시드는 이제 콘텐츠가 채워져 있으므로(아래 테스트), 이 케이스는
        // 전용 빈 픽스처로 검증한다.
        val saved = runAndCapture("seed/interview-prompts-empty-fixture.json")

        assertThat(saved).isEmpty()
        // 빈 시드는 개념을 참조할 일이 없다.
        verifyNoInteractions(conceptRepository)
    }

    @Test
    fun `운영 시드는 문제 시드의 69개 개념 전체를 커버해 전부 조립된다`() {
        given(interviewPromptRepository.count()).willReturn(0L)
        // 문제 시드가 만드는 모든 개념이 존재한다고 가정하고(실제 존재 여부는 InterviewPromptSeedContentTest가 검증),
        // 요청받은 이름 그대로의 개념을 돌려주는 동적 스텁으로 69개 전부의 조립 가능성만 확인한다.
        given(conceptRepository.findByName(anyString())).willAnswer { invocation ->
            Concept(invocation.getArgument(0))
        }

        val saved = runAndCapture("seed/interview-prompts.json")

        assertThat(saved).hasSize(69)
        assertThat(saved).allMatch { it.approvalStatus == ApprovalStatus.APPROVED }
        assertThat(saved.map { it.concept.name }.toSet()).hasSize(69)
    }

    @Nested
    inner class 기동_시_콘텐츠를_업서트한다 {

        @Test
        fun `매칭되는 개념의 면접 질문은 힌트만 갱신한다`() {
            // given: DB에 이미 있는 "스택" 면접 질문의 힌트가 시드 내용과 다르다.
            val stackConcept = Concept("스택")
            val existing = InterviewPrompt(
                approvalStatus = ApprovalStatus.APPROVED,
                concept = stackConcept,
                question = "스택과 큐의 차이를 설명해보세요.",
                modelAnswer = "스택은 후입선출(LIFO), 큐는 선입선출(FIFO) 구조다.",
                rubricPoints = listOf("스택은 LIFO임을 언급", "큐는 FIFO임을 언급"),
                hints = listOf("낡은 힌트"),
            )
            given(interviewPromptRepository.count()).willReturn(1L)
            given(interviewPromptRepository.findAllWithConcept()).willReturn(listOf(existing))
            given(conceptRepository.findByName("스택")).willReturn(stackConcept)

            loader("seed/interview-prompts-fixture.json").run()

            // then: 같은 인스턴스의 힌트가 시드 내용으로 바뀐다.
            assertThat(existing.hints).containsExactly(
                "먼저 넣은 게 먼저 나오는 구조와, 나중에 넣은 게 먼저 나오는 구조를 비교해보세요.",
                "접시 쌓기와 줄 서기를 떠올려보세요.",
            )
        }

        @Test
        fun `힌트가 동일하면 교체하지 않는다`() {
            // given: DB의 힌트가 시드와 완전히 같다.
            val stackConcept = Concept("스택")
            val sameHints = listOf(
                "먼저 넣은 게 먼저 나오는 구조와, 나중에 넣은 게 먼저 나오는 구조를 비교해보세요.",
                "접시 쌓기와 줄 서기를 떠올려보세요.",
            )
            val existing = InterviewPrompt(
                approvalStatus = ApprovalStatus.APPROVED,
                concept = stackConcept,
                question = "스택과 큐의 차이를 설명해보세요.",
                modelAnswer = "스택은 후입선출(LIFO), 큐는 선입선출(FIFO) 구조다.",
                rubricPoints = listOf("스택은 LIFO임을 언급", "큐는 FIFO임을 언급"),
                hints = sameHints,
            )
            given(interviewPromptRepository.count()).willReturn(1L)
            given(interviewPromptRepository.findAllWithConcept()).willReturn(listOf(existing))
            given(conceptRepository.findByName("스택")).willReturn(stackConcept)

            loader("seed/interview-prompts-fixture.json").run()

            // then: 내용이 같으므로 참조 자체가 바뀌지 않아야 한다.
            assertThat(existing.hints).isSameAs(sameHints)
            // 매칭된 기존 질문만 있고 신규 질문이 없으므로 saveAll이 아예 호출되지 않아야 한다.
            verify(interviewPromptRepository, never()).saveAll(anyList())
        }

        @Test
        @Suppress("UNCHECKED_CAST")
        fun `매칭되지 않는 시드 항목은 신규 삽입한다`() {
            // given: DB가 비어있지 않지만(카운트>0), 시드의 개념 이름과 일치하는 기존 행이 없다.
            given(interviewPromptRepository.count()).willReturn(1L)
            given(interviewPromptRepository.findAllWithConcept()).willReturn(emptyList())
            given(conceptRepository.findByName("스택")).willReturn(Concept("스택"))

            val saved = runAndCapture("seed/interview-prompts-fixture.json")

            assertThat(saved).hasSize(1)
            assertThat(saved.single().concept.name).isEqualTo("스택")
        }

        @Test
        fun `DB에는 있지만 시드에 없는 면접 질문은 삭제하지 않는다`() {
            // given: 시드 어디에도 없는 개념의 기존 면접 질문.
            val orphanPrompt = InterviewPrompt(
                approvalStatus = ApprovalStatus.APPROVED,
                concept = Concept("시드에서 이미 빠진 개념"),
                question = "낡은 질문",
                modelAnswer = "낡은 모범 설명",
                rubricPoints = listOf("낡은 포인트"),
            )
            given(interviewPromptRepository.count()).willReturn(1L)
            given(interviewPromptRepository.findAllWithConcept()).willReturn(listOf(orphanPrompt))
            given(conceptRepository.findByName("스택")).willReturn(Concept("스택"))

            loader("seed/interview-prompts-fixture.json").run()

            // then: 삭제 API 호출이 전혀 없어야 한다 — 로그만 남기고 보존.
            verify(interviewPromptRepository, never()).delete(any(InterviewPrompt::class.java))
            verify(interviewPromptRepository, never()).deleteAll(anyIterable())
        }
    }

    @Test
    fun `참조하는 개념이 없으면 기동을 실패시킨다`() {
        given(interviewPromptRepository.count()).willReturn(0L)
        // 문제 시드가 먼저 로드되지 않았거나 개념 이름이 틀린 상황 — 조용히 스킵하지 않고 기동을 실패시킨다.
        given(conceptRepository.findByName(anyString())).willReturn(null)

        assertThatThrownBy { loader("seed/interview-prompts-fixture.json").run() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    /** 로더를 구동해, 저장 직전 조립된 면접 질문 목록을 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun runAndCapture(resourcePath: String): List<InterviewPrompt> {
        loader(resourcePath).run()

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<InterviewPrompt>>
        verify(interviewPromptRepository).saveAll(captor.capture())
        return captor.value
    }
}
