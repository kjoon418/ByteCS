package watson.bytecs.problem.data

import kotlinx.serialization.Serializable
import watson.bytecs.problem.AttemptResult
import watson.bytecs.problem.JudgeResult
import watson.bytecs.problem.ProblemView

/**
 * 백엔드 API 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리해, API 형태가 바뀌어도
 * 매핑 한곳만 고치면 되도록 한다.
 */

/** `GET /api/problems/next` 응답. */
@Serializable
internal data class NextProblemDto(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
) {
    fun toDomain(): ProblemView = ProblemView(
        id = id,
        question = question,
        difficulty = difficulty,
        codeSnippet = codeSnippet,
    )
}

/** `POST /api/problems/{id}/attempts` 요청 본문. */
@Serializable
internal data class AttemptRequestDto(
    val answer: String,
)

/**
 * `POST /api/problems/{id}/attempts` 응답.
 * concept·explanation은 서버가 CORRECT일 때만 채워 보낸다(무낙인·정답 비노출).
 */
@Serializable
internal data class AttemptResponseDto(
    val result: String,
    val concept: String? = null,
    val explanation: String? = null,
) {
    fun toDomain(): AttemptResult = AttemptResult(
        // 서버 enum명과 대소문자 무시 대조. 미지값은 명확한 예외로 올려(여전히 뷰모델이 catch)
        // 판정 오류를 네트워크 오류로 오인하지 않게 한다.
        result = JudgeResult.entries.find { it.name.equals(result, ignoreCase = true) }
            ?: throw IllegalStateException("알 수 없는 판정 결과: $result"),
        concept = concept,
        explanation = explanation,
    )
}
