package watson.bytecs.session.data

import kotlinx.serialization.Serializable
import watson.bytecs.problem.JudgeResult
import watson.bytecs.problem.data.EnrichmentDto
import watson.bytecs.session.AttemptOutcome
import watson.bytecs.session.DailySession
import watson.bytecs.session.HintReveal
import watson.bytecs.session.PastItem
import watson.bytecs.session.Reveal
import watson.bytecs.session.SessionHint
import watson.bytecs.session.SessionProblem
import watson.bytecs.session.SessionStatus
import watson.bytecs.session.Streak
import watson.bytecs.session.UnlockedIntegration

/**
 * 백엔드 `/api/sessions` 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리해, API 형태가 바뀌어도
 * 매핑 한곳만 고치면 되게 한다.
 */

/** 판정 문자열을 [JudgeResult]로 매핑. 미지값은 명확한 예외로 올려 네트워크 오류와 구분한다(문제 슬라이스와 동일). */
private fun String.toJudgeResult(): JudgeResult =
    JudgeResult.entries.find { it.name.equals(this, ignoreCase = true) }
        ?: throw IllegalStateException("알 수 없는 판정 결과: $this")

/** 힌트 본문 하나. no-leak: 미공개 힌트는 서버가 이 목록에 넣지 않는다(공개된 것만). */
@Serializable
internal data class HintDto(
    val text: String,
    val codeSnippet: String? = null,
) {
    fun toDomain(): SessionHint = SessionHint(text, codeSnippet)
}

@Serializable
internal data class SessionProblemDto(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
    // 힌트: 전체 수(hintCount)만 항상 싣고, 본문은 공개된 것(revealedHints)만 싣는다(no-leak, 인수인계 §3.3).
    val hintCount: Int = 0,
    val revealedHints: List<HintDto> = emptyList(),
    // 대표 분류(명세 §7). 개념명과 달리 no-leak 대상이 아니라 풀기 전부터 실린다. 미분류면 null.
    val category: String? = null,
    // 이 칸에 누적된 비정답 횟수(D2 — 재시도 안내의 근거). 서버가 원천이라 재진입해도 정확하다.
    val wrongAttemptCount: Int = 0,
) {
    fun toDomain(): SessionProblem = SessionProblem(
        id = id,
        question = question,
        difficulty = difficulty,
        codeSnippet = codeSnippet,
        hintCount = hintCount,
        revealedHints = revealedHints.map { it.toDomain() },
        category = category,
        wrongAttemptCount = wrongAttemptCount,
    )
}

@Serializable
internal data class StreakDto(
    val count: Int,
    val lastStudyDate: String? = null,
) {
    fun toDomain(): Streak = Streak(count, lastStudyDate)
}

/**
 * `GET /api/sessions/today` 응답. streak는 백엔드가 read 경로에 실어 주면 채워진다(아직 미제공이면 null).
 * needsDifficultyPrompt는 완료 화면 난이도 제안 카드 노출 여부(DF1) — 서버 단일 출처.
 */
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
    val needsDifficultyPrompt: Boolean = false,
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
        needsDifficultyPrompt = needsDifficultyPrompt,
    )
}

/** 세션 완료로 새로 열린 지정 연결 문제 하나(D2) — 구성 개념명 목록만(태깅 순). */
@Serializable
internal data class UnlockedIntegrationDto(
    val concepts: List<String> = emptyList(),
) {
    fun toDomain(): UnlockedIntegration = UnlockedIntegration(concepts)
}

/** `POST /api/sessions/today/attempts` 응답. */
@Serializable
internal data class SessionAttemptResponseDto(
    val result: String,
    val status: String,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val concepts: List<String>? = null,
    val explanation: String? = null,
    val currentProblem: SessionProblemDto? = null,
    val streak: StreakDto? = null,
    val misconceptionHint: String? = null,
    val enrichment: EnrichmentDto? = null,
    // 화면 표시용 대표 정답. 서버가 CORRECT일 때만 채워 보낸다(무낙인·정답 비노출 연장).
    val representativeAnswer: String? = null,
    // 이 제출로 완료됐고 난이도 제안을 노출해야 할 때만 true(미완료 제출은 항상 false).
    val needsDifficultyPrompt: Boolean = false,
    // 이 제출로 완료되며 새로 열린 지정 연결 문제들(D2). 미완료·해제 없음이면 빈 목록(서버가 생략하면 기본값).
    val unlockedIntegrations: List<UnlockedIntegrationDto> = emptyList(),
) {
    fun toDomain(): AttemptOutcome = AttemptOutcome(
        result = result.toJudgeResult(),
        status = SessionStatus.from(status),
        solvedCount = solvedCount,
        totalCount = totalCount,
        position = position,
        concepts = concepts,
        explanation = explanation,
        currentProblem = currentProblem?.toDomain(),
        streak = streak?.toDomain(),
        misconceptionHint = misconceptionHint,
        enrichment = enrichment?.toDomain(),
        representativeAnswer = representativeAnswer,
        needsDifficultyPrompt = needsDifficultyPrompt,
        unlockedIntegrations = unlockedIntegrations.map { it.toDomain() },
    )
}

/** `POST /api/sessions/today/attempts` 요청 본문. */
@Serializable
internal data class SessionAttemptRequestDto(
    val answer: String,
)

/**
 * 서버 공통 오류 본문(`{message, errorCode}`). 세션 예외를 상태 코드가 아니라 [errorCode]로 구별한다
 * — ITEM_NOT_VIEWABLE(403)과 SESSION_ALREADY_COMPLETED(409)가 상태만으로는 안 갈리기 때문.
 */
@Serializable
internal data class ErrorBodyDto(
    val message: String? = null,
    val errorCode: String? = null,
)

/** `POST /api/sessions/today/reveal` 응답. */
@Serializable
internal data class RevealResponseDto(
    val concepts: List<String>,
    val explanation: String? = null,
    // 화면 표시용 대표 정답 하나. 허용답 나열은 응답에서 사라졌다([2026-07-16] 오너 결정).
    val representativeAnswer: String,
    val enrichment: EnrichmentDto? = null,
    // 대표 분류(명세 §7). 미분류면 null.
    val category: String? = null,
) {
    fun toDomain(): Reveal = Reveal(concepts, explanation, representativeAnswer, enrichment?.toDomain(), category)
}

/**
 * `POST /api/sessions/today/hints/reveal` 요청 본문. 클라가 아는 현재 공개 수를 싣는다 —
 * 서버는 현재 [revealedCount]와 일치할 때만 +1 한다(더블탭·경쟁 안전).
 */
@Serializable
internal data class HintRevealRequestDto(
    val revealedCount: Int,
)

/** `POST /api/sessions/today/hints/reveal` 응답(공개 후 전체 목록). */
@Serializable
internal data class HintStateResponseDto(
    val hintCount: Int,
    val revealedHints: List<HintDto> = emptyList(),
) {
    fun toDomain(): HintReveal = HintReveal(hintCount, revealedHints.map { it.toDomain() })
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
    val concepts: List<String>,
    val explanation: String? = null,
    // 화면 표시용 대표 정답 하나. 허용답 나열은 응답에서 사라졌다([2026-07-16] 오너 결정).
    val representativeAnswer: String,
    val enrichment: EnrichmentDto? = null,
    // 대표 분류(명세 §7). 미분류면 null.
    val category: String? = null,
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
        concepts = concepts,
        explanation = explanation,
        representativeAnswer = representativeAnswer,
        enrichment = enrichment?.toDomain(),
        category = category,
    )
}
