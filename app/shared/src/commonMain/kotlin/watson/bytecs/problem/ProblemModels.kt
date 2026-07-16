package watson.bytecs.problem

/**
 * 문제 풀이 슬라이스의 도메인 모델.
 * 백엔드 API 계약(GET /api/problems/next, POST /api/problems/{id}/attempts)과 형태를 정확히 맞춘다.
 */

/**
 * 다음 문제. 정답을 유추할 수 있는 정보(개념명·허용답)는 포함하지 않는다.
 * `GET /api/problems/next` → {id, question, difficulty?, codeSnippet?}
 */
data class ProblemView(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
)

/** 결정적 판정 결과. 같은 입력 → 같은 판정(AI 판정 없음). */
enum class JudgeResult {
    /** 허용답과 정규화 후 정확히 일치. */
    CORRECT,

    /** 오탈자 수준(편집거리 임계 내)으로 가까움. */
    NEAR_MISS,

    /** 불일치. */
    MISMATCH,
}

/**
 * 답 제출 결과. 개념·해설·심화 정보·대표 정답은 **정답(CORRECT)일 때만** 채워지고, 불일치·근접에는 null이다
 * (무낙인·정답 비노출 원칙). [concepts]는 태깅 순서를 보존한 목록(첫 번째가 대표 개념).
 * [enrichment]는 '더 알아보기'(§5.7) — 없어도 되는 선택 콘텐츠다.
 * [representativeAnswer]는 화면 표시용 대표 정답 하나([2026-07-16] 오너 결정).
 * `POST /api/problems/{id}/attempts {answer}` → {result, concepts?, explanation?, enrichment?, representativeAnswer?}
 */
data class AttemptResult(
    val result: JudgeResult,
    val concepts: List<String>? = null,
    val explanation: String? = null,
    val enrichment: String? = null,
    val representativeAnswer: String? = null,
)
