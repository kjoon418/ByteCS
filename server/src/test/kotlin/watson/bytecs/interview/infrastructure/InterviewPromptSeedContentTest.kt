package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import watson.bytecs.problem.infrastructure.ProblemSeedFile

/**
 * 면접 질문 시드([interview-prompts.json])가 문제 시드([problems-generated.json])의 개념 전체를 커버하는지
 * 결정적으로 검증한다. Spring 컨텍스트 없이 두 리소스를 직접 읽어 비교한다.
 *
 * 이 커버리지가 "주관식 문제 풀이 → 면접 질문 해금 → 면접 문제 풀이" 흐름을 **모든** 테스터가 겪을 수 있게 하는
 * 유일한 보장이다(어떤 개념을 먼저 마스터하든 그 개념의 면접 질문이 항상 존재해야 한다) — 나중에 문제 시드에
 * 새 개념이 추가됐는데 이 시드가 갱신되지 않으면 이 테스트가 즉시 잡는다.
 */
class InterviewPromptSeedContentTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `면접 질문 시드는 문제 시드의 모든 개념을 정확히 한 번씩 커버한다`() {
        val problemConcepts = loadProblemSeed().problems.flatMap { it.concepts }.toSet()
        val promptConcepts = loadPromptSeed().prompts.map { it.concept }

        assertThat(promptConcepts.toSet())
            .`as`("면접 질문이 없는 개념이 있으면 그 개념을 먼저 마스터한 테스터는 면접 세션을 만나지 못한다")
            .isEqualTo(problemConcepts)
        assertThat(promptConcepts)
            .`as`("개념당 면접 질문은 정확히 1개(1차 운영 기본)")
            .hasSize(promptConcepts.toSet().size)
    }

    @Test
    fun `면접 질문 시드의 모든 항목이 구조적으로 유효하다`() {
        val prompts = loadPromptSeed().prompts

        assertThat(prompts).isNotEmpty
        prompts.forEach { prompt ->
            assertThat(prompt.concept).`as`("concept").isNotBlank()
            assertThat(prompt.question).`as`("question of ${prompt.concept}").isNotBlank()
            assertThat(prompt.modelAnswer).`as`("modelAnswer of ${prompt.concept}").isNotBlank()
            assertThat(prompt.rubricPoints).`as`("rubricPoints of ${prompt.concept}").isNotEmpty()
            assertThat(prompt.rubricPoints).`as`("no blank rubric point of ${prompt.concept}").allMatch { it.isNotBlank() }
        }
    }

    private fun loadProblemSeed(): ProblemSeedFile =
        ClassPathResource("seed/problems-generated.json").inputStream.use {
            objectMapper.readValue(it, ProblemSeedFile::class.java)
        }

    private fun loadPromptSeed(): InterviewPromptSeedFile =
        ClassPathResource("seed/interview-prompts.json").inputStream.use {
            objectMapper.readValue(it, InterviewPromptSeedFile::class.java)
        }
}
