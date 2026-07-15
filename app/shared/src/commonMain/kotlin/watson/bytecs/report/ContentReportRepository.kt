package watson.bytecs.report

/**
 * 콘텐츠 오류 신고 데이터 접근 계약(07 화면 · 수용 기준 17번).
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접 다루지 않는다.
 */
interface ContentReportRepository {
    /**
     * 문제의 콘텐츠 오류를 신고한다. `POST /api/problems/{problemId}/reports`.
     * [message]는 빈 문자열이 아니어야 한다(빈 신고는 화면·서버 모두 거부).
     */
    suspend fun report(problemId: Long, message: String)

    /** 보유 자원(HTTP 클라이언트 등)을 정리한다. 자원이 없는 구현은 no-op. */
    fun close() {}
}

/**
 * 백엔드 없이 신고 흐름(성공/실패)을 결정적으로 재현하는 인메모리 구현.
 * [failWith]를 주면 항상 그 예외를 던져 전송 실패 경로를 검증할 수 있다.
 */
class FakeContentReportRepository(
    private val failWith: Throwable? = null,
) : ContentReportRepository {

    /** 접수된 신고(문제 id → 메시지). 검증용. */
    val submitted = mutableListOf<Pair<Long, String>>()

    override suspend fun report(problemId: Long, message: String) {
        failWith?.let { throw it }
        submitted.add(problemId to message)
    }
}
