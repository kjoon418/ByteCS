package watson.bytecs.session.data

import kotlinx.serialization.Serializable
import watson.bytecs.problem.JudgeResult
import watson.bytecs.session.AttemptOutcome
import watson.bytecs.session.DailySession
import watson.bytecs.session.PastItem
import watson.bytecs.session.Reveal
import watson.bytecs.session.SessionProblem
import watson.bytecs.session.SessionStatus
import watson.bytecs.session.Streak

/**
 * 백엔드 `/api/sessions` 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리해, API 형태가 바뀌어도
 * 매핑 한곳만 고치면 되게 한다.
 */

/** 판정 문자열을 [JudgeResult]로 매핑. 미지값은 명확한 예외로 올려 네트워크 오류와 구분한다(문제 슬라이스와 동일). */
private fun String.toJudgeResult(): JudgeResult =
    JudgeResult.entries.find { it.name.equals(this, ignoreCase = true) }
        ?: throw IllegalStateException("알 수 없는 판정 결과: $this")

@Serializable
internal data class SessionProblemDto(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
) {
    fun toDomain(): SessionProblem = SessionProblem(id, question, difficulty, codeSnippet)
}

@Serializable
internal data class StreakDto(
    val count: Int,
    val lastStudyDate: String? = null,
) {
    fun toDomain(): Streak = Streak(count, lastStudyDate)
}

/** `GET /api/sessions/today` 응답. streak는 백엔드가 read 경로에 실어 주면 채워진다(아직 미제공이면 null). */
@Serializable
internal data class SessionStateDto(
    val sessionId: Long,
    val sessionDate: String,
    val status: String,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val currentProblem: SessionProblemDto? = null,
    val streak: StreakDto? = null,
) {
    fun toDomain(): DailySession = DailySession(
        sessionId = sessionId,
        sessionDate = sessionDate,
        status = SessionStatus.from(status),
        solvedCount = solvedCount,
        totalCount = totalCount,
        position = position,
        currentProblem = currentProblem?.toDomain(),
        streak = streak?.toDomain(),
    )
}

/** `POST /api/sessions/today/attempts` 응답. */
@Serializable
internal data class SessionAttemptResponseDto(
    val result: String,
    val status: String,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val concept: String? = null,
    val explanation: String? = null,
    val currentProblem: SessionProblemDto? = null,
    val streak: StreakDto? = null,
) {
    fun toDomain(): AttemptOutcome = AttemptOutcome(
        result = result.toJudgeResult(),
        status = SessionStatus.from(status),
        solvedCount = solvedCount,
        totalCount = totalCount,
        position = position,
        concept = concept,
        explanation = explanation,
        currentProblem = currentProblem?.toDomain(),
        streak = streak?.toDomain(),
    )
}

/** `POST /api/sessions/today/attempts` 요청 본문. */
@Serializable
internal data class SessionAttemptRequestDto(
    val answer: String,
)

/**
 * 서버 공통 오류 본문(`{message, errorCode}`). 세션 예외를 상태 코드가 아니라 [errorCode]로 구별한다
 * — ITEM_NOT_VIEWABLE(403)과 SESSION_ALREADY_COMPLETED·REVEAL_NOT_ALLOWED(409)가 상태만으로는 안 갈리기 때문.
 */
@Serializable
internal data class ErrorBodyDto(
    val message: String? = null,
    val errorCode: String? = null,
)

/** `POST /api/sessions/today/reveal` 응답. */
@Serializable
internal data class RevealResponseDto(
    val concept: String,
    val explanation: String? = null,
    val acceptableAnswers: List<String>,
) {
    fun toDomain(): Reveal = Reveal(concept, explanation, acceptableAnswers)
}

/** `GET /api/sessions/today/items/{position}` 응답. */
@Serializable
internal data class PastItemResponseDto(
    val position: Int,
    val problemId: Long,
    val question: String,
    val codeSnippet: String? = null,
    val difficulty: String? = null,
    val submittedAnswer: String? = null,
    val result: String,
    val revealed: Boolean,
    val concept: String,
    val explanation: String? = null,
    val acceptableAnswers: List<String>,
) {
    fun toDomain(): PastItem = PastItem(
        position = position,
        problemId = problemId,
        question = question,
        codeSnippet = codeSnippet,
        difficulty = difficulty,
        submittedAnswer = submittedAnswer,
        result = result.toJudgeResult(),
        revealed = revealed,
        concept = concept,
        explanation = explanation,
        acceptableAnswers = acceptableAnswers,
    )
}
