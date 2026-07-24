package watson.bytecs.interview.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import watson.bytecs.interview.domain.InterviewEligibility
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.interview.infrastructure.InterviewPromptRepository
import watson.bytecs.problem.domain.Concept
import watson.bytecs.review.infrastructure.ConceptMasteryRepository

/**
 * 정답 시 **신규 면접 승급** 판정(DI9)의 경계를 검증한다: recordSolve 전후 후보 스냅샷의 차이로 '이번 정답으로 새로 열린' 개념만
 * 가려내고, 승인 질문이 있는 개념만 남기며, 이미 열려 있던 개념·아직 임계 미달인 개념은 제외한다.
 * 저장소는 목이라 DB 없이 각 분기를 결정적으로 재현한다(면접 후보 임계는 면접 세션 후보 산정과 같은 쿼리·같은 값을 쓴다).
 */
class InterviewUnlockCalculatorTest {

    private val conceptMasteryRepository: ConceptMasteryRepository = mock(ConceptMasteryRepository::class.java)
    private val interviewPromptRepository: InterviewPromptRepository = mock(InterviewPromptRepository::class.java)
    private val calculator = InterviewUnlockCalculator(conceptMasteryRepository, interviewPromptRepository)

    private val userId = 1L

    @Test
    fun `eligibleConceptIdsBefore는 이번 문제 개념 중 이미 후보인 것만 추린다`() {
        stubEligible(10L, 20L, 99L) // 사용자의 현재 후보 개념(레벨≥임계)

        val before = calculator.eligibleConceptIdsBefore(userId, conceptIds = listOf(10L, 30L))

        // 30은 후보가 아니고, 99는 이번 문제 개념이 아니다 → 교집합 {10}만.
        assertThat(before).containsExactly(10L)
    }

    @Test
    fun `정답으로 개념이 처음 후보가 되고 승인 질문이 있으면 개념명을 돌려준다`() {
        // 목 프롬프트는 먼저 만든다 — given(...).willReturn(...) 인자 안에서 approvedPrompt가 또 given을 호출하면 미완성 스터빙이 된다.
        val prompt = approvedPrompt(conceptId = 10L, conceptName = "프로세스")
        stubEligible(10L) // recordSolve 후: 10이 후보가 됐다
        given(interviewPromptRepository.findApprovedByConceptIdIn(listOf(10L))).willReturn(listOf(prompt))

        val unlocked = calculator.newlyUnlockedConceptNames(userId, conceptIds = listOf(10L), eligibleBefore = emptySet())

        assertThat(unlocked).containsExactly("프로세스")
    }

    @Test
    fun `후보가 됐어도 승인된 면접 질문이 없으면 제외한다`() {
        stubEligible(10L)
        given(interviewPromptRepository.findApprovedByConceptIdIn(listOf(10L))).willReturn(emptyList())

        val unlocked = calculator.newlyUnlockedConceptNames(userId, conceptIds = listOf(10L), eligibleBefore = emptySet())

        assertThat(unlocked).isEmpty()
    }

    @Test
    fun `이미 열려 있던 개념은 다시 맞혀도 새로 열림에서 제외한다`() {
        stubEligible(10L) // 여전히 후보지만

        val unlocked = calculator.newlyUnlockedConceptNames(userId, conceptIds = listOf(10L), eligibleBefore = setOf(10L))

        assertThat(unlocked).isEmpty()
        // 새로 열린 게 없으면 승인 질문 조회조차 하지 않는다(불필요한 왕복 회피).
        verifyNoInteractions(interviewPromptRepository)
    }

    @Test
    fun `아직 후보 임계에 못 미친 개념은 제외한다`() {
        stubEligible() // recordSolve 후에도 후보 없음(도움 정답 등으로 레벨 미달)

        val unlocked = calculator.newlyUnlockedConceptNames(userId, conceptIds = listOf(10L), eligibleBefore = emptySet())

        assertThat(unlocked).isEmpty()
    }

    @Test
    fun `여러 개념이 새로 열리면 태깅 순서를 보존해 개념명을 돌려준다`() {
        val promptA = approvedPrompt(conceptId = 10L, conceptName = "A")
        val promptB = approvedPrompt(conceptId = 30L, conceptName = "B")
        stubEligible(10L, 30L)
        given(interviewPromptRepository.findApprovedByConceptIdIn(listOf(30L, 10L)))
            .willReturn(listOf(promptA, promptB))

        // 입력(태깅) 순서가 [30, 10]이면 결과도 그 순서를 따른다.
        val unlocked = calculator.newlyUnlockedConceptNames(userId, conceptIds = listOf(30L, 10L), eligibleBefore = emptySet())

        assertThat(unlocked).containsExactly("B", "A")
    }

    @Test
    fun `개념이 없는 문제면 후보 조회 없이 빈 결과다`() {
        val before = calculator.eligibleConceptIdsBefore(userId, conceptIds = emptyList())
        val unlocked = calculator.newlyUnlockedConceptNames(userId, conceptIds = emptyList(), eligibleBefore = emptySet())

        assertThat(before).isEmpty()
        assertThat(unlocked).isEmpty()
        // 개념이 없으면 후보 조회·승인 질문 조회를 아예 하지 않는다(빈 입력 조기 반환).
        verifyNoInteractions(conceptMasteryRepository, interviewPromptRepository)
    }

    /** 사용자의 현재 후보 개념 id(레벨≥임계)를 스텁한다 — 면접 세션 후보 산정과 같은 쿼리·임계. */
    private fun stubEligible(vararg conceptIds: Long) {
        given(
            conceptMasteryRepository
                .findConceptIdsByUserIdAndLevelGreaterThanEqual(userId, InterviewEligibility.MASTERY_LEVEL),
        ).willReturn(conceptIds.toList())
    }

    /** 개념 id·이름만 필요한 승인 면접 질문을 목으로 만든다(엔티티 id가 generated라 목으로 값을 고정한다). */
    private fun approvedPrompt(conceptId: Long, conceptName: String): InterviewPrompt {
        val concept = mock(Concept::class.java)
        given(concept.id).willReturn(conceptId)
        given(concept.name).willReturn(conceptName)
        val prompt = mock(InterviewPrompt::class.java)
        given(prompt.concept).willReturn(concept)
        return prompt
    }
}
