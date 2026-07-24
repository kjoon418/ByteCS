package watson.bytecs.interview.data

import watson.bytecs.interview.InterviewReadiness
import watson.bytecs.interview.InterviewSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 유선 DTO → 도메인 매핑([InterviewAnswerResponseDto.toDomain]·[InterviewSessionDto.toDomain])을 직접 검증한다.
 * Fake 저장소는 도메인 객체를 곧장 만들어 이 매핑을 우회하므로, no-leak nullable 처리·준비도 파생·불변식(requireNotNull)은
 * 여기서만 커버된다(리뷰 지적: 매핑 무테스트 구간).
 */
class InterviewDtosTest {

    private fun answer(
        judged: Boolean,
        points: List<RubricPointResultDto> = emptyList(),
        nextQuestion: String? = null,
        nextConceptName: String? = null,
        nextPromptId: Long? = null,
        practicedConceptCount: Int? = null,
        streak: InterviewStreakDto? = null,
    ) = InterviewAnswerResponseDto(
        judged = judged,
        points = points,
        comment = "코멘트",
        modelAnswer = "모범 설명",
        conceptName = "프로세스와 스레드",
        status = if (nextQuestion == null) "COMPLETED" else "IN_PROGRESS",
        position = 0,
        totalCount = 3,
        nextQuestion = nextQuestion,
        nextConceptName = nextConceptName,
        nextPromptId = nextPromptId,
        practicedConceptCount = practicedConceptCount,
        streak = streak,
    )

    @Test
    fun 전_포인트_충족이면_검증됨으로_파생한다() {
        val outcome = answer(
            judged = true,
            points = listOf(RubricPointResultDto("p1", true), RubricPointResultDto("p2", true)),
        ).toDomain()

        assertEquals(InterviewReadiness.VERIFIED, outcome.result.readiness)
        assertTrue(outcome.result.verified)
        assertEquals(false, outcome.result.fallback)
        assertEquals(2, outcome.result.points.size)
    }

    @Test
    fun 일부만_충족이면_부분으로_파생한다() {
        val outcome = answer(
            judged = true,
            points = listOf(RubricPointResultDto("p1", true), RubricPointResultDto("p2", false)),
        ).toDomain()

        assertEquals(InterviewReadiness.PARTIAL, outcome.result.readiness)
        assertEquals(false, outcome.result.verified)
    }

    @Test
    fun 모두_미충족이면_미검증으로_파생한다() {
        val outcome = answer(
            judged = true,
            points = listOf(RubricPointResultDto("p1", false)),
        ).toDomain()

        assertEquals(InterviewReadiness.UNVERIFIED, outcome.result.readiness)
    }

    @Test
    fun 폴백이면_포인트가_비고_미검증이며_모범설명은_유지된다() {
        val outcome = answer(judged = false).toDomain()

        assertTrue(outcome.result.fallback)
        assertEquals(InterviewReadiness.UNVERIFIED, outcome.result.readiness)
        assertEquals(0, outcome.result.points.size)
        assertEquals("모범 설명", outcome.modelAnswer)
        // no-leak: DI10 미제공이라 항상 null.
        assertNull(outcome.reviewProblemId)
    }

    @Test
    fun 다음_문항이_있으면_다음_아이템을_만든다() {
        val outcome = answer(
            judged = true,
            points = listOf(RubricPointResultDto("p1", true)),
            nextQuestion = "다음 질문",
            nextConceptName = "캐시",
            nextPromptId = 200L,
        ).toDomain()

        assertEquals(InterviewSessionStatus.IN_PROGRESS, outcome.status)
        assertEquals("다음 질문", outcome.nextItem?.question)
        assertEquals("캐시", outcome.nextItem?.conceptName)
        assertEquals(200L, outcome.nextItem?.promptId)
    }

    @Test
    fun 다음_질문은_있는데_개념명이_없으면_불변식으로_거부한다() {
        assertFailsWith<IllegalArgumentException> {
            answer(judged = true, nextQuestion = "다음 질문", nextConceptName = null, nextPromptId = 200L).toDomain()
        }
    }

    @Test
    fun 다음_질문은_있는데_promptId가_없으면_불변식으로_거부한다() {
        assertFailsWith<IllegalArgumentException> {
            answer(judged = true, nextQuestion = "다음 질문", nextConceptName = "캐시", nextPromptId = null).toDomain()
        }
    }

    @Test
    fun 완료_요약과_스트릭이_있으면_completion에_실린다() {
        val outcome = answer(
            judged = true,
            points = listOf(RubricPointResultDto("p1", true)),
            practicedConceptCount = 3,
            streak = InterviewStreakDto(count = 5, lastStudyDate = "2026-07-24"),
        ).toDomain()

        assertEquals(3, outcome.completion?.practicedConceptCount)
        assertEquals(5, outcome.completion?.streak?.count)
    }

    @Test
    fun 세션_DTO는_현재_질문으로_아이템을_만든다() {
        val session = InterviewSessionDto(
            sessionId = 1L,
            sessionDate = "2026-07-24",
            status = "IN_PROGRESS",
            position = 1,
            totalCount = 3,
            currentQuestion = "지금 질문",
            currentConceptName = "인덱스",
            currentPromptId = 300L,
        ).toDomain()

        assertEquals(1, session.currentItem?.position)
        assertEquals("지금 질문", session.currentItem?.question)
        assertEquals("인덱스", session.currentItem?.conceptName)
        assertEquals(300L, session.currentItem?.promptId)
    }

    @Test
    fun 세션_DTO에_현재_질문이_없으면_아이템도_없다() {
        val session = InterviewSessionDto(
            sessionId = 1L,
            sessionDate = "2026-07-24",
            status = "COMPLETED",
            position = 3,
            totalCount = 3,
            currentQuestion = null,
        ).toDomain()

        assertNull(session.currentItem)
        assertTrue(session.isCompleted)
    }

    @Test
    fun 세션_DTO에_현재_질문은_있는데_promptId가_없으면_불변식으로_거부한다() {
        assertFailsWith<IllegalArgumentException> {
            InterviewSessionDto(
                sessionId = 1L,
                sessionDate = "2026-07-24",
                status = "IN_PROGRESS",
                position = 0,
                totalCount = 3,
                currentQuestion = "지금 질문",
                currentConceptName = "인덱스",
                currentPromptId = null,
            ).toDomain()
        }
    }
}
