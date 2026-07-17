package watson.bytecs.extrastudy.data

import kotlinx.serialization.Serializable
import watson.bytecs.extrastudy.ExtraStudyAttempt
import watson.bytecs.extrastudy.ExtraStudyHint
import watson.bytecs.extrastudy.ExtraStudyHintReveal
import watson.bytecs.extrastudy.ExtraStudyProblem
import watson.bytecs.extrastudy.ExtraStudyReveal
import watson.bytecs.extrastudy.ExtraStudyState
import watson.bytecs.problem.JudgeResult
import watson.bytecs.problem.data.EnrichmentDto

/**
 * 백엔드 `/api/extra-study` 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리해, API 형태가 바뀌어도
 * 매핑 한곳만 고치면 되게 한다. 심화 구조는 공용 [EnrichmentDto]를 재사용한다(계약 §B — session·scrap과 공유).
 */

/** 판정 문자열을 [JudgeResult]로 매핑. 미지값은 명확한 예외로 올려 네트워크 오류와 구분한다(세션 슬라이스와 동일). */
private fun String.toJudgeResult(): JudgeResult =
    JudgeResult.entries.find { it.name.equals(this, ignoreCase = true) }
        ?: throw IllegalStateException("알 수 없는 판정 결과: $this")

/** 힌트 본문 하나. no-leak: 미공개 힌트는 서버가 이 목록에 넣지 않는다(공개된 것만). */
@Serializable
internal data class ExtraStudyHintDto(
    val text: String,
    val codeSnippet: String? = null,
) {
    fun toDomain(): ExtraStudyHint = ExtraStudyHint(text, codeSnippet)
}

@Serializable
internal data class ExtraStudyProblemDto(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
    // 힌트: 전체 수(hintCount)만 항상 싣고, 본문은 공개된 것(revealedHints)만 싣는다(no-leak).
    val hintCount: Int = 0,
    val revealedHints: List<ExtraStudyHintDto> = emptyList(),
    // 대표 분류(명세 §7). no-leak 대상이 아니라 풀기 전부터 실린다. 미분류면 null.
    val category: String? = null,
) {
    fun toDomain(): ExtraStudyProblem = ExtraStudyProblem(
        id = id,
        question = question,
        difficulty = difficulty,
        codeSnippet = codeSnippet,
        hintCount = hintCount,
        revealedHints = revealedHints.map { it.toDomain() },
        category = category,
    )
}

/**
 * `GET /api/extra-study/current` 응답. 열린 문제가 있으면 `exhausted:false`+`problem`, 소진이면
 * `exhausted:true`+`problem:null`. 소진은 오류가 아니라 정상 GET 상태다(별도 에러코드 없음).
 */
@Serializable
internal data class ExtraStudyCurrentResponseDto(
    val exhausted: Boolean,
    val problem: ExtraStudyProblemDto? = null,
) {
    fun toDomain(): ExtraStudyState =
        if (exhausted || problem == null) {
            ExtraStudyState.Exhausted
        } else {
            ExtraStudyState.Available(problem.toDomain())
        }
}

/** `POST /api/extra-study/attempts` 요청 본문. */
@Serializable
internal data class ExtraStudyAttemptRequestDto(
    val answer: String,
)

/**
 * `POST /api/extra-study/attempts` 응답. concepts·explanation·enrichment·representativeAnswer는 서버가
 * CORRECT일 때만 채워 보낸다(무낙인·정답 비노출). 세션 attempt 응답에서 세션 전용 필드(status·solvedCount·
 * totalCount·position·streak·currentProblem)만 뺀 모양이다.
 */
@Serializable
internal data class ExtraStudyAttemptResponseDto(
    val result: String,
    val concepts: List<String>? = null,
    val explanation: String? = null,
    val enrichment: EnrichmentDto? = null,
    val representativeAnswer: String? = null,
    val misconceptionHint: String? = null,
) {
    fun toDomain(): ExtraStudyAttempt = ExtraStudyAttempt(
        result = result.toJudgeResult(),
        concepts = concepts,
        explanation = explanation,
        enrichment = enrichment?.toDomain(),
        representativeAnswer = representativeAnswer,
        misconceptionHint = misconceptionHint,
    )
}

/** `POST /api/extra-study/reveal` 응답(세션 RevealResponse와 동형). */
@Serializable
internal data class ExtraStudyRevealResponseDto(
    val concepts: List<String>,
    val explanation: String? = null,
    // 화면 표시용 대표 정답 하나. 허용답 나열은 응답에서 하지 않는다([2026-07-16] 오너 결정).
    val representativeAnswer: String,
    val enrichment: EnrichmentDto? = null,
    // 대표 분류(명세 §7). 미분류면 null.
    val category: String? = null,
) {
    fun toDomain(): ExtraStudyReveal =
        ExtraStudyReveal(concepts, explanation, representativeAnswer, enrichment?.toDomain(), category)
}

/**
 * `POST /api/extra-study/hints/reveal` 요청 본문. 클라가 아는 현재 공개 수를 싣는다 —
 * 서버는 현재 [revealedCount]와 일치할 때만 +1 한다(더블탭·경쟁 안전).
 */
@Serializable
internal data class ExtraStudyHintRevealRequestDto(
    val revealedCount: Int,
)

/** `POST /api/extra-study/hints/reveal` 응답(공개 후 전체 목록, 세션 HintRevealResponse와 동형). */
@Serializable
internal data class ExtraStudyHintRevealResponseDto(
    val hintCount: Int,
    val revealedHints: List<ExtraStudyHintDto> = emptyList(),
) {
    fun toDomain(): ExtraStudyHintReveal =
        ExtraStudyHintReveal(hintCount, revealedHints.map { it.toDomain() })
}

/**
 * 서버 공통 오류 본문(`{message, errorCode}`). 추가 학습 경합을 상태 코드가 아니라 [errorCode]로 구별한다
 * (EXTRA_STUDY_NO_OPEN_ITEM=409).
 */
@Serializable
internal data class ExtraStudyErrorBodyDto(
    val message: String? = null,
    val errorCode: String? = null,
)
