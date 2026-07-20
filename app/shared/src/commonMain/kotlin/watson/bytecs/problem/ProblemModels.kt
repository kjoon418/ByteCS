package watson.bytecs.problem

/**
 * 공용 도메인 leaf 타입. 원래 문제 풀이 슬라이스의 모델이었으나, 레거시 추가 연습 화면이 폐기되고
 * (세션으로 통일, D6·D9) 나면서 이 패키지는 '피처 슬라이스'가 아니라 **공용 도메인 leaf + HTTP 인프라**
 * 홈으로 남는다. 아래 타입들은 session·scrap 슬라이스가 공유한다(중복 정의 방지).
 */

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
 * 연장 — 문제 배포 응답에는 절대 포함되지 않는다). session·scrap 슬라이스가 이 타입을 공유한다.
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
