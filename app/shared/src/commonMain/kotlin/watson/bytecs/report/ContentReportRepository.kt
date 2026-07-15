package watson.bytecs.report

/**
 * 콘텐츠 오류 신고 데이터 접근 계약(07 화면 · 수용 기준 17번).
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접 다루지 않는다.
 */
interface ContentReportRepository {
    /**
     * 문제의 콘텐츠 오류를 신고한다. `POST /api/problems/{problemId}/reports`.
     * [category]는 필수(서버가 미지원 값을 거부), [message]는 선택(비어도 제출 가능).
     */
    suspend fun report(problemId: Long, category: ReportCategory, message: String?)

    /** 보유 자원(HTTP 클라이언트 등)을 정리한다. 자원이 없는 구현은 no-op. */
    fun close() {}
}

/**
 * 신고 유형(단일 선택, 필수). 라벨은 클라이언트 라이팅이고 서버는 [name]을 코드로 저장한다
 * (team-plan.md §B [계약 v2]).
 */
enum class ReportCategory(val label: String) {
    WRONG_ANSWER("정답이 틀려요"),
    QUESTION_ERROR("문제 설명에 오류가 있어요"),
    HINT_ERROR("힌트에 오류가 있어요"),
    OTHER("기타"),
}

/**
 * 백엔드 없이 신고 흐름(성공/실패)을 결정적으로 재현하는 인메모리 구현.
 * [failWith]를 주면 항상 그 예외를 던져 전송 실패 경로를 검증할 수 있다.
 */
class FakeContentReportRepository(
    private val failWith: Throwable? = null,
) : ContentReportRepository {

    /** 접수된 신고(문제 id, 유형, 상세 내용). 검증용. */
    val submitted = mutableListOf<Triple<Long, ReportCategory, String?>>()

    override suspend fun report(problemId: Long, category: ReportCategory, message: String?) {
        failWith?.let { throw it }
        submitted.add(Triple(problemId, category, message))
    }
}
