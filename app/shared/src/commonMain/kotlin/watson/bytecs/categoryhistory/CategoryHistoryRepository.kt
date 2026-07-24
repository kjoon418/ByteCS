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

    /**
     * 본인이 푼 문제 하나를 id로 읽기 전용 재열람한다. `GET /api/learning-history/problems/{problemId}`.
     * 면접 결과의 '그때 푼 문제 다시 보기'(DI10)가 쓴다 — 카테고리 분류와 무관하게 열린다(미분류 개념의 문제도).
     * 풀지 않았거나 미승인된 문제는 서버가 404로 응답한다(예외로 올라온다).
     */
    suspend fun getSolvedProblem(problemId: Long): CategoryHistoryItem
}

/**
 * 백엔드 없이 카테고리별 이력을 결정적으로 재현하는 인메모리 구현.
 * [getFailWith]를 주면 조회가 항상 실패해 오류 경로를 검증할 수 있다.
 * [solvedProblem]/[solvedProblemFailWith]로 단건 재열람(DI10) 경로를 스크립팅한다.
 */
class FakeCategoryHistoryRepository(
    private val groups: List<CategoryHistoryGroup> = emptyList(),
    private val getFailWith: Throwable? = null,
    private val solvedProblem: CategoryHistoryItem? = null,
    private val solvedProblemFailWith: Throwable? = null,
) : CategoryHistoryRepository {

    override suspend fun getByCategory(): List<CategoryHistoryGroup> {
        getFailWith?.let { throw it }
        return groups
    }

    override suspend fun getSolvedProblem(problemId: Long): CategoryHistoryItem {
        solvedProblemFailWith?.let { throw it }
        return solvedProblem ?: groups.flatMap { it.items }.first { it.problemId == problemId }
    }
}
