package watson.bytecs.problem

/**
 * 문제 데이터 접근 계약. 실제 구현(Ktor client)은 이후 통합 태스크에서 배선한다.
 */
interface ProblemRepository {
    /** 다음 본 문제를 가져온다. */
    suspend fun getNext(): ProblemView

    /** 답을 제출하고 결정적 판정 결과를 받는다. */
    suspend fun submitAttempt(problemId: Long, answer: String): AttemptResult

    /** 보유 자원(HTTP 클라이언트 등)을 정리한다. 자원이 없는 구현은 no-op. */
    fun close() {}
}

/**
 * 백엔드가 아직 연결되지 않은 동안 세 피드백 상태(CORRECT/NEAR_MISS/MISMATCH)를 실제로
 * 재현하기 위한 인메모리 구현. 판정 로직은 서버 `Problem.judge`와 동일한 규칙을 따른다.
 *
 * 서버 대비 단순화: 유니코드 NFC 정규화는 생략한다(commonMain에 `java.text.Normalizer`가 없음).
 * 트림·소문자화·내부 공백 축약은 동일하게 적용해, 정확 일치와 근접 판정이 같은 기준을 공유한다.
 */
class FakeProblemRepository(
    private val problems: List<Seed> = defaultSeeds,
) : ProblemRepository {

    /**
     * 시드 문제. [acceptableAnswers]는 판정에만 쓰고 [ProblemView]로는 노출하지 않는다
     * (정답 비노출).
     */
    data class Seed(
        val id: Long,
        val question: String,
        val acceptableAnswers: Set<String>,
        val concepts: List<String>,
        val difficulty: String? = null,
        val codeSnippet: String? = null,
        val explanation: String? = null,
    )

    private var cursor = 0

    override suspend fun getNext(): ProblemView {
        val seed = problems[cursor % problems.size]
        cursor++
        return ProblemView(
            id = seed.id,
            question = seed.question,
            difficulty = seed.difficulty,
            codeSnippet = seed.codeSnippet,
        )
    }

    override suspend fun submitAttempt(problemId: Long, answer: String): AttemptResult {
        val seed = problems.firstOrNull { it.id == problemId }
            ?: return AttemptResult(JudgeResult.MISMATCH)

        return when (judge(answer, seed.acceptableAnswers)) {
            JudgeResult.CORRECT -> AttemptResult(
                result = JudgeResult.CORRECT,
                concepts = seed.concepts,
                explanation = seed.explanation,
            )
            // ⭐️ 불일치·근접에는 개념·해설을 노출하지 않는다.
            JudgeResult.NEAR_MISS -> AttemptResult(JudgeResult.NEAR_MISS)
            JudgeResult.MISMATCH -> AttemptResult(JudgeResult.MISMATCH)
        }
    }

    // ── 결정적 판정 (서버 Problem.judge 미러링) ──────────────────────────────

    private fun judge(raw: String, acceptableAnswers: Set<String>): JudgeResult {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return JudgeResult.MISMATCH

        val normalizedAcceptable = acceptableAnswers.map { normalize(it) }
        if (normalized in normalizedAcceptable) return JudgeResult.CORRECT

        val isNearMiss = normalizedAcceptable.any { acceptable ->
            acceptable.length >= MIN_NEAR_MISS_LENGTH &&
                levenshtein(normalized, acceptable) <= nearMissThreshold(acceptable.length)
        }
        return if (isNearMiss) JudgeResult.NEAR_MISS else JudgeResult.MISMATCH
    }

    private fun normalize(raw: String): String =
        raw.trim().lowercase().replace(WHITESPACE, " ")

    private fun levenshtein(source: String, target: String): Int {
        if (source.isEmpty()) return target.length
        if (target.isEmpty()) return source.length

        var previousRow = IntArray(target.length + 1) { it }
        var currentRow = IntArray(target.length + 1)

        for (i in 1..source.length) {
            currentRow[0] = i
            for (j in 1..target.length) {
                val substitutionCost = if (source[i - 1] == target[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,
                    previousRow[j] + 1,
                    previousRow[j - 1] + substitutionCost,
                )
            }
            val temp = previousRow
            previousRow = currentRow
            currentRow = temp
        }
        return previousRow[target.length]
    }

    private fun nearMissThreshold(acceptableLength: Int): Int =
        if (acceptableLength <= SHORT_ANSWER_MAX_LENGTH) SHORT_ANSWER_THRESHOLD else LONG_ANSWER_THRESHOLD

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private const val MIN_NEAR_MISS_LENGTH = 3
        private const val SHORT_ANSWER_MAX_LENGTH = 7
        private const val SHORT_ANSWER_THRESHOLD = 1
        private const val LONG_ANSWER_THRESHOLD = 2

        /** 서버 시더의 문제 일부를 그대로 옮긴 샘플. 세 상태를 모두 시연 가능. */
        val defaultSeeds = listOf(
            Seed(
                id = 1L,
                question = "한 프로세스 안에서 스택 등 일부를 제외한 자원을 공유하며 실행되는 흐름의 단위는?",
                acceptableAnswers = setOf("스레드", "쓰레드", "thread"),
                concepts = listOf("프로세스와 스레드"),
                difficulty = "EASY",
                explanation = "스레드는 프로세스의 코드·데이터·힙을 공유하되, 스택과 레지스터는 각자 가진다.",
            ),
            Seed(
                id = 2L,
                question = "다음 코드의 시간 복잡도를 빅오 표기법으로 나타내면?",
                acceptableAnswers = setOf("o(n^2)", "o(n²)", "n^2", "오엔제곱"),
                concepts = listOf("시간 복잡도"),
                difficulty = "MEDIUM",
                codeSnippet = """
                    for (i in 0 until n) {
                        for (j in 0 until n) {
                            println(i * j)
                        }
                    }
                """.trimIndent(),
                explanation = "이중 반복문이 각각 n번 돌아 n×n = n² 번 수행된다.",
            ),
        )
    }
}
