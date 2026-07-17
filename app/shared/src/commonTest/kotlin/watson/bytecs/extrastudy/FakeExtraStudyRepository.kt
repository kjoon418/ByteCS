package watson.bytecs.extrastudy

import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.JudgeResult

/**
 * 결정적 인메모리 [ExtraStudyRepository]. VM 상태 전이를 네트워크 없이 검증한다.
 * 현재 상태는 [current]로, 제출 결과는 [onSubmit]로 스크립팅하고, 오류는 필드로 주입한다.
 */
class FakeExtraStudyRepository(
    var current: ExtraStudyState = ExtraStudyState.Available(problem()),
) : ExtraStudyRepository {

    var getCurrentError: Throwable? = null
    var submitError: Throwable? = null
    var revealError: Throwable? = null
    var revealHintError: Throwable? = null

    var revealResult: ExtraStudyReveal =
        ExtraStudyReveal(concepts = listOf("스택"), explanation = "LIFO 구조", representativeAnswer = "스택 (stack)")

    /** 힌트 열기 결과 공급자. 인자는 클라가 보낸 현재 공개 수. 기본은 +1(약→강 스크립트). */
    var onRevealHint: (Int) -> ExtraStudyHintReveal = { count ->
        ExtraStudyHintReveal(hintCount = 2, revealedHints = defaultHints.take(count + 1))
    }

    /** 제출 결과 공급자. 기본은 불일치(무진행). */
    var onSubmit: (String) -> ExtraStudyAttempt = { mismatchAttempt() }

    val submitted = mutableListOf<String>()
    var getCurrentCount = 0
    var submitCount = 0
    var revealCount = 0
    var revealHintCount = 0
    var lastRevealHintCount: Int? = null

    override suspend fun getCurrent(): ExtraStudyState {
        getCurrentCount++
        getCurrentError?.let { throw it }
        return current
    }

    override suspend fun submitAttempt(answer: String): ExtraStudyAttempt {
        submitCount++
        submitted += answer
        submitError?.let { throw it }
        return onSubmit(answer)
    }

    override suspend fun reveal(): ExtraStudyReveal {
        revealCount++
        revealError?.let { throw it }
        return revealResult
    }

    override suspend fun revealHint(revealedCount: Int): ExtraStudyHintReveal {
        revealHintCount++
        lastRevealHintCount = revealedCount
        revealHintError?.let { throw it }
        return onRevealHint(revealedCount)
    }

    companion object {
        val defaultHints = listOf(
            ExtraStudyHint(text = "약한 힌트"),
            ExtraStudyHint(text = "강한 힌트"),
        )

        fun problem(
            id: Long = 1L,
            hintCount: Int = 0,
            revealedHints: List<ExtraStudyHint> = emptyList(),
        ) = ExtraStudyProblem(
            id = id,
            question = "Q$id",
            difficulty = "EASY",
            codeSnippet = null,
            hintCount = hintCount,
            revealedHints = revealedHints,
        )

        fun available(problem: ExtraStudyProblem = problem()) = ExtraStudyState.Available(problem)

        fun correctAttempt(
            enrichment: Enrichment? = null,
            representativeAnswer: String? = "대표정답",
        ) = ExtraStudyAttempt(
            result = JudgeResult.CORRECT,
            concepts = listOf("개념"),
            explanation = "해설",
            enrichment = enrichment,
            representativeAnswer = representativeAnswer,
        )

        fun mismatchAttempt(misconceptionHint: String? = null) = ExtraStudyAttempt(
            result = JudgeResult.MISMATCH,
            misconceptionHint = misconceptionHint,
        )

        fun nearMissAttempt() = ExtraStudyAttempt(result = JudgeResult.NEAR_MISS)
    }
}
