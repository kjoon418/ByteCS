package watson.bytecs.interview.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.interview.application.dto.RubricPointResultResponse
import watson.bytecs.interview.domain.ExplanationJudge
import watson.bytecs.interview.domain.InterviewMemberOnlyException
import watson.bytecs.interview.domain.InterviewNoCandidateException
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.interview.domain.InterviewQuotaExceededException
import watson.bytecs.interview.domain.InterviewReadiness
import watson.bytecs.interview.domain.InterviewReadinessStatus
import watson.bytecs.interview.domain.InterviewSession
import watson.bytecs.interview.domain.JudgeResult
import watson.bytecs.interview.infrastructure.InterviewPromptRepository
import watson.bytecs.interview.infrastructure.InterviewReadinessRepository
import watson.bytecs.interview.infrastructure.InterviewSessionRepository
import watson.bytecs.problem.domain.Concept
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/**
 * 면접 세션의 승급 후보 산정·쿼터·준비도 갱신·복습 당김(DI11)·스트릭 OR(DI5) 조율을 검증한다.
 * 스레드 경합 대신 협력자 스텁으로 각 분기를 결정적으로 재현한다(SessionServiceTest 관례).
 *
 * [review-todo] 컨트롤러 통합 테스트(MockMvc·보안 체인 배선)는 이 슬라이스에서 다루지 않았다 — 별도 검토 필요.
 */
class InterviewSessionServiceTest {

    private val interviewSessionRepository: InterviewSessionRepository = mock(InterviewSessionRepository::class.java)
    private val interviewPromptRepository: InterviewPromptRepository = mock(InterviewPromptRepository::class.java)
    private val interviewReadinessRepository: InterviewReadinessRepository = mock(InterviewReadinessRepository::class.java)
    private val conceptMasteryRepository: ConceptMasteryRepository = mock(ConceptMasteryRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val explanationJudge: ExplanationJudge = mock(ExplanationJudge::class.java)

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val today: LocalDate = LocalDate.of(2026, 7, 23)
    private val clock: Clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private val service = InterviewSessionService(
        interviewSessionRepository,
        interviewPromptRepository,
        interviewReadinessRepository,
        conceptMasteryRepository,
        userRepository,
        explanationJudge,
        InterviewResponseMapper(),
        clock,
    )

    @Test
    fun `게스트가 세션을 생성하려 하면 예외가 발생한다`() {
        val guest = User.createGuest()
        stubUser(guest)

        assertThatThrownBy { service.createTodaySession(1L) }
            .isInstanceOf(InterviewMemberOnlyException::class.java)
    }

    @Test
    fun `승급 후보 개념이 없으면 예외가 발생한다`() {
        stubUser(memberUser())
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(null)
        given(interviewSessionRepository.countGradedSessionsOn(1L, today)).willReturn(0L)
        given(conceptMasteryRepository.findConceptIdsByUserIdAndLevelGreaterThanEqual(1L, 1)).willReturn(emptyList())

        assertThatThrownBy { service.createTodaySession(1L) }
            .isInstanceOf(InterviewNoCandidateException::class.java)
    }

    @Test
    fun `오늘 채점 성공 세션이 있으면 쿼터 소진 예외가 발생하고 후보를 조회하지 않는다`() {
        stubUser(memberUser())
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(null)
        given(interviewSessionRepository.countGradedSessionsOn(1L, today)).willReturn(1L)

        assertThatThrownBy { service.createTodaySession(1L) }
            .isInstanceOf(InterviewQuotaExceededException::class.java)
        verify(interviewPromptRepository, never()).findApproved()
    }

    @Test
    fun `진행 중인 오늘 세션이 있으면 새로 만들지 않고 재개한다`() {
        stubUser(memberUser())
        val prompt = mockPrompt(id = 100L, conceptId = 1L, question = "Q1")
        val existing = InterviewSession.assign(1L, today, listOf(100L))
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(existing)
        given(interviewPromptRepository.findById(100L)).willReturn(Optional.of(prompt))

        val response = service.createTodaySession(1L)

        assertThat(response.currentQuestion).isEqualTo("Q1")
        verify(interviewPromptRepository, never()).findApproved()
    }

    @Test
    fun `승급 후보로 새 세션을 만들며 미검증 우선으로 정렬한다`() {
        stubUser(memberUser())
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(null)
        given(interviewSessionRepository.countGradedSessionsOn(1L, today)).willReturn(0L)
        given(conceptMasteryRepository.findConceptIdsByUserIdAndLevelGreaterThanEqual(1L, 1))
            .willReturn(listOf(1L, 2L, 3L))

        val promptForVerified = mockPrompt(id = 10L, conceptId = 1L, question = "Q1")
        val promptForUnverified = mockPrompt(id = 20L, conceptId = 2L, question = "Q2")
        val promptForPartial = mockPrompt(id = 30L, conceptId = 3L, question = "Q3")
        given(interviewPromptRepository.findApproved())
            .willReturn(listOf(promptForVerified, promptForUnverified, promptForPartial))

        val readinessVerified = mock(InterviewReadiness::class.java)
        given(readinessVerified.conceptId).willReturn(1L)
        given(readinessVerified.status).willReturn(InterviewReadinessStatus.VERIFIED)
        val readinessPartial = mock(InterviewReadiness::class.java)
        given(readinessPartial.conceptId).willReturn(3L)
        given(readinessPartial.status).willReturn(InterviewReadinessStatus.PARTIAL)
        given(interviewReadinessRepository.findByUserIdAndConceptIdIn(1L, setOf(1L, 2L, 3L)))
            .willReturn(listOf(readinessVerified, readinessPartial))

        // 우선순위: 미검증(개념2) > 부분(개념3) > 검증됨(개념1) → 첫 칸은 개념2의 질문(Q2)이어야 한다.
        given(interviewPromptRepository.findById(20L)).willReturn(Optional.of(promptForUnverified))

        val response = service.createTodaySession(1L)

        assertThat(response.currentQuestion).isEqualTo("Q2")
        assertThat(response.totalCount).isEqualTo(3)
    }

    @Test
    fun `한 개념에 면접 질문이 여럿이면 id가 가장 작은 것으로 결정적으로 고른다`() {
        stubUser(memberUser())
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(null)
        given(interviewSessionRepository.countGradedSessionsOn(1L, today)).willReturn(0L)
        given(conceptMasteryRepository.findConceptIdsByUserIdAndLevelGreaterThanEqual(1L, 1)).willReturn(listOf(2L))

        // 같은 개념(2L)에 승인 질문이 둘 — id가 큰 것을 먼저 반환해도 최소 id(10L)가 선택되어야 한다.
        val chosen = mockPrompt(id = 10L, conceptId = 2L, question = "선택된 질문")
        val other = mockPrompt(id = 30L, conceptId = 2L, question = "탈락 질문")
        given(interviewPromptRepository.findApproved()).willReturn(listOf(other, chosen))
        given(interviewReadinessRepository.findByUserIdAndConceptIdIn(1L, setOf(2L))).willReturn(emptyList())
        given(interviewPromptRepository.findById(10L)).willReturn(Optional.of(chosen))

        val response = service.createTodaySession(1L)

        assertThat(response.currentQuestion).isEqualTo("선택된 질문")
        assertThat(response.totalCount).isEqualTo(1)
    }

    @Test
    fun `채점 성공이고 전 포인트 미달이면 준비도를 갱신하고 복습 시점을 당긴다`() {
        stubUser(memberUser())
        val prompt = mockPrompt(
            id = 100L,
            conceptId = 5L,
            question = "Q",
            modelAnswer = "모범 설명",
            rubricPoints = listOf("p1", "p2"),
        )
        val session = InterviewSession.assign(1L, today, listOf(100L))
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(session)
        given(interviewPromptRepository.findById(100L)).willReturn(Optional.of(prompt))
        given(explanationJudge.judge(listOf("p1", "p2"), "설명"))
            .willReturn(JudgeResult(listOf(true, false), "코멘트"))

        val readiness = InterviewReadiness.initial(1L, 5L)
        given(interviewReadinessRepository.findByUserIdAndConceptId(1L, 5L)).willReturn(readiness)

        // level 1 → 2 → 3으로 밀어 사다리[3]=14일(오늘보다 훨씬 뒤)까지 벌려, 당김(면접일+1)이 실제로 이르게 만든다.
        val mastery = ConceptMastery.firstSolve(1L, 5L, MasterySignal.UNAIDED, today, 999L)
        mastery.applySolve(MasterySignal.UNAIDED, today, 999L)
        mastery.applySolve(MasterySignal.UNAIDED, today, 999L)
        given(conceptMasteryRepository.findByUserIdAndConceptId(1L, 5L)).willReturn(mastery)

        val response = service.submitAnswer(1L, "설명")

        assertThat(response.judged).isTrue()
        assertThat(response.points).containsExactly(
            RubricPointResultResponse("p1", true),
            RubricPointResultResponse("p2", false),
        )
        assertThat(readiness.status).isEqualTo(InterviewReadinessStatus.PARTIAL)
        assertThat(readiness.satisfiedCount).isEqualTo(1)
        assertThat(mastery.nextReviewDate).isEqualTo(today.plusDays(1))
        assertThat(mastery.level).isEqualTo(3) // 레벨은 무변경
    }

    @Test
    fun `채점 성공이고 전 포인트 충족이면 복습 시점을 당기지 않는다`() {
        stubUser(memberUser())
        val prompt = mockPrompt(id = 100L, conceptId = 5L, question = "Q", rubricPoints = listOf("p1"))
        val session = InterviewSession.assign(1L, today, listOf(100L))
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(session)
        given(interviewPromptRepository.findById(100L)).willReturn(Optional.of(prompt))
        given(explanationJudge.judge(listOf("p1"), "설명")).willReturn(JudgeResult(listOf(true), "코멘트"))
        given(interviewReadinessRepository.findByUserIdAndConceptId(1L, 5L))
            .willReturn(InterviewReadiness.initial(1L, 5L))

        service.submitAnswer(1L, "설명")

        verify(conceptMasteryRepository, never()).findByUserIdAndConceptId(1L, 5L)
    }

    @Test
    fun `채점 폴백이면 준비도와 복습 시점을 갱신하지 않는다`() {
        stubUser(memberUser())
        val prompt = mockPrompt(
            id = 100L,
            conceptId = 5L,
            question = "Q",
            modelAnswer = "모범 설명",
            rubricPoints = listOf("p1"),
        )
        val session = InterviewSession.assign(1L, today, listOf(100L))
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(session)
        given(interviewPromptRepository.findById(100L)).willReturn(Optional.of(prompt))
        given(explanationJudge.judge(listOf("p1"), "설명")).willReturn(null)

        val response = service.submitAnswer(1L, "설명")

        assertThat(response.judged).isFalse()
        assertThat(response.modelAnswer).isEqualTo("모범 설명")
        verify(interviewReadinessRepository, never()).findByUserIdAndConceptId(1L, 5L)
        verify(conceptMasteryRepository, never()).findByUserIdAndConceptId(1L, 5L)
    }

    @Test
    fun `마지막 질문에 답하면 세션이 완료되고 스트릭을 기록한다`() {
        val member = memberUser()
        stubUser(member)
        val prompt = mockPrompt(id = 100L, conceptId = 5L, question = "Q", rubricPoints = listOf("p1"))
        val session = InterviewSession.assign(1L, today, listOf(100L))
        given(interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(1L, today)).willReturn(session)
        given(interviewPromptRepository.findById(100L)).willReturn(Optional.of(prompt))
        given(explanationJudge.judge(listOf("p1"), "설명")).willReturn(JudgeResult(listOf(true), "코멘트"))
        given(interviewReadinessRepository.findByUserIdAndConceptId(1L, 5L))
            .willReturn(InterviewReadiness.initial(1L, 5L))

        val response = service.submitAnswer(1L, "설명")

        assertThat(response.status).isEqualTo("COMPLETED")
        assertThat(response.streak?.count).isEqualTo(1)
        assertThat(member.streak.lastStudyDate).isEqualTo(today)
    }

    @Test
    fun `게스트 상태 조회는 잔여 쿼터 0과 게스트 여부를 돌려준다`() {
        stubUser(User.createGuest())
        given(conceptMasteryRepository.findConceptIdsByUserIdAndLevelGreaterThanEqual(1L, 1)).willReturn(emptyList())

        val status = service.getStatus(1L)

        assertThat(status.isGuest).isTrue()
        assertThat(status.remainingQuota).isEqualTo(0)
        assertThat(status.candidateConceptCount).isEqualTo(0)
    }

    private fun memberUser(): User = User.createMember(Email("test@example.com"), "hash")

    private fun stubUser(user: User) {
        given(userRepository.findById(1L)).willReturn(Optional.of(user))
        given(userRepository.findWithLockById(1L)).willReturn(Optional.of(user))
    }

    private fun mockPrompt(
        id: Long,
        conceptId: Long,
        question: String,
        modelAnswer: String = "모범 설명",
        rubricPoints: List<String> = listOf("포인트1"),
    ): InterviewPrompt {
        val concept = mock(Concept::class.java)
        given(concept.id).willReturn(conceptId)
        given(concept.name).willReturn("개념$conceptId")
        val prompt = mock(InterviewPrompt::class.java)
        given(prompt.id).willReturn(id)
        given(prompt.concept).willReturn(concept)
        given(prompt.question).willReturn(question)
        given(prompt.modelAnswer).willReturn(modelAnswer)
        given(prompt.rubricPoints).willReturn(rubricPoints)
        return prompt
    }
}
