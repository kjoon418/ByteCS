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
 * '더 알아보기' 심화 정보(§5.7)의 구조. [2026-07-16] 오너 결정 — 자유 텍스트 한 덩어리 대신 시안 구조
 * (제목·리드·항목 카드·인용)로 고정한다. 정답 처리 이후에만 채워지고, 없으면 섹션 자체가 생략된다(no-leak
 * 연장 — 문제 배포 응답에는 절대 포함되지 않는다). session·scrap 슬라이스도 이 타입을 공유한다.
 *  - [title]: 본 카드 제목. [body]: 리드 문단.
 *  - [items]: 보조 항목(제목+설명) 0개 이상, 순서 보존.
 *  - [quote]: 인용(선택) — 어울리는 문제에만 있다.
 */
data class Enrichment(
    val title: String,
    val body: String,
    val items: List<EnrichmentItem> = emptyList(),
    val quote: String? = null,
)

/** [Enrichment]의 보조 항목 카드 하나. */
data class EnrichmentItem(
    val title: String,
    val description: String,
)

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
    val enrichment: Enrichment? = null,
    val representativeAnswer: String? = null,
)
