package watson.bytecs.session

import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.JudgeResult

/**
 * 결정적 인메모리 [SessionRepository]. VM 상태 전이를 네트워크 없이 검증한다.
 * 제출 결과는 [onSubmit]로 스크립팅하고, 오류는 필드로 주입한다.
 */
class FakeSessionRepository(
    var today: DailySession = activeSession(),
) : SessionRepository {

    var getTodayError: Throwable? = null
    var markStartedError: Throwable? = null
    var submitError: Throwable? = null
    var revealError: Throwable? = null
    var pastError: Throwable? = null
    var revealHintError: Throwable? = null
    var startNextError: Throwable? = null

    /** '조금 더 풀기'가 호출될 때 돌려줄 세션. 기본은 [today]와 같아 별도로 스크립팅하지 않아도 동작한다. */
    var nextSession: DailySession? = null
    var startNextCount = 0

    var revealResult: Reveal =
        Reveal(concepts = listOf("스택"), explanation = "LIFO 구조", representativeAnswer = "스택 (stack)")
    var pastResult: PastItem = pastItem(position = 0)

    /** 힌트 열기 결과 공급자. 인자는 클라가 보낸 현재 공개 수. 기본은 +1(약→강 스크립트). */
    var onRevealHint: (Int) -> HintReveal = { count ->
        HintReveal(hintCount = 2, revealedHints = defaultHints.take(count + 1))
    }

    val submitted = mutableListOf<String>()
    var markStartedCount = 0
    var submitCount = 0
    var revealCount = 0
    var revealHintCount = 0
    var lastRevealHintCount: Int? = null
    var lastPastPosition: Int? = null

    /** 제출 결과 공급자. 기본은 불일치(무진행). */
    var onSubmit: (String) -> AttemptOutcome = { mismatchOutcome() }

    override suspend fun getToday(): DailySession {
        getTodayError?.let { throw it }
        return today
    }

    override suspend fun startNextSession(): DailySession {
        startNextCount++
        startNextError?.let { throw it }
        return nextSession ?: today
    }

    override suspend fun markStarted() {
        markStartedCount++
        markStartedError?.let { throw it }
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

    override suspend fun revealHint(revealedCount: Int): HintReveal {
        revealHintCount++
        lastRevealHintCount = revealedCount
        revealHintError?.let { throw it }
        return onRevealHint(revealedCount)
    }

    override suspend fun getPastItem(position: Int): PastItem {
        lastPastPosition = position
        pastError?.let { throw it }
        return pastResult.copy(position = position)
    }

    companion object {
        val defaultHints = listOf(
            SessionHint(text = "약한 힌트"),
            SessionHint(text = "강한 힌트"),
        )

        fun problem(
            id: Long = 1L,
            hintCount: Int = 0,
            revealedHints: List<SessionHint> = emptyList(),
            wrongAttemptCount: Int = 0,
        ) = SessionProblem(
            id = id,
            question = "Q$id",
            difficulty = "EASY",
            codeSnippet = null,
            hintCount = hintCount,
            revealedHints = revealedHints,
            wrongAttemptCount = wrongAttemptCount,
        )

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
            enrichment: Enrichment? = null,
            representativeAnswer: String? = "대표정답",
        ) = AttemptOutcome(
            result = JudgeResult.CORRECT,
            status = if (completed) SessionStatus.COMPLETED else SessionStatus.IN_PROGRESS,
            solvedCount = solved,
            totalCount = total,
            position = position,
            concepts = listOf("개념"),
            explanation = "해설",
            currentProblem = next,
            streak = streak,
            enrichment = enrichment,
            representativeAnswer = representativeAnswer,
        )

        fun mismatchOutcome(total: Int = 3, misconceptionHint: String? = null) = AttemptOutcome(
            result = JudgeResult.MISMATCH,
            status = SessionStatus.IN_PROGRESS,
            solvedCount = 0,
            totalCount = total,
            position = 0,
            concepts = null,
            explanation = null,
            currentProblem = problem(1L),
            streak = null,
            misconceptionHint = misconceptionHint,
        )

        fun nearMissOutcome(total: Int = 3) = mismatchOutcome(total).copy(result = JudgeResult.NEAR_MISS)

        fun pastItem(position: Int, enrichment: Enrichment? = null) = PastItem(
            position = position,
            problemId = 10L + position,
            question = "지난 Q$position",
            codeSnippet = null,
            difficulty = "EASY",
            submittedAnswer = "내답",
            result = JudgeResult.CORRECT,
            revealed = false,
            concepts = listOf("개념$position"),
            explanation = "해설$position",
            representativeAnswer = "정답$position",
            enrichment = enrichment,
        )
    }
}
