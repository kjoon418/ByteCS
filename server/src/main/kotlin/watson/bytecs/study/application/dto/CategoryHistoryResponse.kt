package watson.bytecs.study.application.dto

/**
 * 카테고리별 학습 이력 조회(GET /api/learning-history/categories) 응답의 한 그룹.
 * 8개 고정 대분류(명세 §7) 전체가 항상 실리며, category 순서는 [watson.bytecs.problem.domain.ProblemCategory] 선언 순서를 따른다.
 * 그 카테고리에 푼 문제가 없으면 items가 빈 목록이다 — 클라는 이를 오류가 아니라 '준비 중'(긍정 빈 상태)으로 렌더한다.
 */
data class CategoryHistoryResponse(
    val category: String,
    val items: List<CategoryHistoryItemResponse>,
)
