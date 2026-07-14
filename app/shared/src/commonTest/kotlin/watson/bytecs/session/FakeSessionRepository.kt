package watson.bytecs.session

import watson.bytecs.problem.JudgeResult

/**
 * 결정적 인메모리 [SessionRepository]. VM 상태 전이를 네트워크 없이 검증한다.
 * 제출 결과는 [onSubmit]로 스크립팅하고, 오류는 필드로 주입한다.
 */
class FakeSessionRepository(
    var today: DailySession = activeSession(),
) : SessionRepository {

    var getTodayError: Throwable? = null
    var submitError: Throwable? = null
    var revealError: Throwable? = null
    var pastError: Throwable? = null

    var revealResult: Reveal = Reveal(concept = "스택", explanation = "LIFO 구조", acceptableAnswers = listOf("스택", "stack"))
    var pastResult: PastItem = pastItem(position = 0)

    val submitted = mutableListOf<String>()
    var submitCount = 0
    var revealCount = 0
    var lastPastPosition: Int? = null

    /** 제출 결과 공급자. 기본은 불일치(무진행). */
    var onSubmit: (String) -> AttemptOutcome = { mismatchOutcome() }

    override suspend fun getToday(): DailySession {
        getTodayError?.let { throw it }
        return today
    }

    override suspend fun submitAttempt(answer: String): AttemptOutcome {
        submitCount++
        submitted += answer
        submitError?.let { throw it }
        return onSubmit(answer)
    }

    override suspend fun reveal(): Reveal {
        revealCount++
        revealError?.let { throw it }
        return revealResult
    }

    override suspend fun getPastItem(position: Int): PastItem {
        lastPastPosition = position
        pastError?.let { throw it }
        return pastResult.copy(position = position)
    }

    companion object {
        fun problem(id: Long = 1L) = SessionProblem(id = id, question = "Q$id", difficulty = "EASY", codeSnippet = null)

        fun activeSession(
            position: Int = 0,
            total: Int = 3,
            solved: Int = 0,
            problem: SessionProblem? = problem(1L),
            streak: Streak? = null,
        ) = DailySession(
            sessionId = 1L,
            sessionDate = "2026-07-14",
            status = SessionStatus.IN_PROGRESS,
            solvedCount = solved,
            totalCount = total,
            position = position,
            currentProblem = problem,
            streak = streak,
        )

        fun completedSession(total: Int = 3, streak: Streak? = Streak(5, "2026-07-14")) = DailySession(
            sessionId = 1L,
            sessionDate = "2026-07-14",
            status = SessionStatus.COMPLETED,
            solvedCount = total,
            totalCount = total,
            position = total,
            currentProblem = null,
            streak = streak,
        )

        fun correctOutcome(
            next: SessionProblem?,
            position: Int,
            solved: Int,
            total: Int = 3,
            completed: Boolean = false,
            streak: Streak? = null,
        ) = AttemptOutcome(
            result = JudgeResult.CORRECT,
            status = if (completed) SessionStatus.COMPLETED else SessionStatus.IN_PROGRESS,
            solvedCount = solved,
            totalCount = total,
            position = position,
            concept = "개념",
            explanation = "해설",
            currentProblem = next,
            streak = streak,
        )

        fun mismatchOutcome(total: Int = 3) = AttemptOutcome(
            result = JudgeResult.MISMATCH,
            status = SessionStatus.IN_PROGRESS,
            solvedCount = 0,
            totalCount = total,
            position = 0,
            concept = null,
            explanation = null,
            currentProblem = problem(1L),
            streak = null,
        )

        fun nearMissOutcome(total: Int = 3) = mismatchOutcome(total).copy(result = JudgeResult.NEAR_MISS)

        fun pastItem(position: Int) = PastItem(
            position = position,
            problemId = 10L + position,
            question = "지난 Q$position",
            codeSnippet = null,
            difficulty = "EASY",
            submittedAnswer = "내답",
            result = JudgeResult.CORRECT,
            revealed = false,
            concept = "개념$position",
            explanation = "해설$position",
            acceptableAnswers = listOf("정답$position"),
        )
    }
}
