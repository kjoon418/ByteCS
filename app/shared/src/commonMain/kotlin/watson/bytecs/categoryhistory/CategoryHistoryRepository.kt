package watson.bytecs.categoryhistory

/**
 * 카테고리별 학습 이력 데이터 접근 계약(도메인 명세 §7, 1차 · 읽기 전용). 본인이 푼 문제만 다룬다
 * (사용자 격리는 서버가 보장). 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접
 * 다루지 않는다.
 */
interface CategoryHistoryRepository {
    /**
     * 카테고리별 학습 이력. `GET /api/learning-history/categories`.
     * 8개 고정 대분류가 [watson.bytecs.problem.domain.ProblemCategory] 선언 순서로 항상 전부 실린다
     * (문제가 없는 카테고리는 빈 목록).
     */
    suspend fun getByCategory(): List<CategoryHistoryGroup>
}

/**
 * 백엔드 없이 카테고리별 이력을 결정적으로 재현하는 인메모리 구현.
 * [getFailWith]를 주면 조회가 항상 실패해 오류 경로를 검증할 수 있다.
 */
class FakeCategoryHistoryRepository(
    private val groups: List<CategoryHistoryGroup> = emptyList(),
    private val getFailWith: Throwable? = null,
) : CategoryHistoryRepository {

    override suspend fun getByCategory(): List<CategoryHistoryGroup> {
        getFailWith?.let { throw it }
        return groups
    }
}
