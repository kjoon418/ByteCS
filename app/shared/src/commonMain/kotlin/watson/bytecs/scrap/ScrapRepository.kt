package watson.bytecs.scrap

/**
 * 문제 스크랩 데이터 접근 계약(기능 5 · 수용 기준 22번). 본인 스크랩만 다룬다(사용자 격리는 서버가 보장).
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접 다루지 않는다.
 */
interface ScrapRepository {
    /** 내 스크랩 목록. `GET /api/scraps`. */
    suspend fun list(): List<ScrapListItem>

    /** 스크랩한 문제의 읽기 전용 재열람(문제·모범답안·해설). `GET /api/scraps/{problemId}`. */
    suspend fun get(problemId: Long): ScrapDetail

    /** 스크랩 추가. 멱등(이미 스크랩이면 no-op). `POST /api/problems/{problemId}/scraps`. */
    suspend fun add(problemId: Long)

    /** 스크랩 해제. 없으면 no-op. `DELETE /api/problems/{problemId}/scraps`. */
    suspend fun remove(problemId: Long)

    /** 보유 자원(HTTP 클라이언트 등)을 정리한다. 자원이 없는 구현은 no-op. */
    fun close() {}
}

/**
 * 백엔드 없이 스크랩 목록·재열람·토글을 결정적으로 재현하는 인메모리 구현.
 * [listFailWith]를 주면 목록 조회가 항상 실패해 오류 경로를 검증할 수 있다.
 */
class FakeScrapRepository(
    seeds: List<ScrapDetail> = emptyList(),
    // ⭐️ 서버 계약(Instant 직렬화)과 형태를 맞춘 값 — 화면이 formatScrappedAt으로 파싱·표기한다.
    private val scrappedAt: String = "2026-07-15T09:00:00Z",
    private val listFailWith: Throwable? = null,
    private val toggleFailWith: Throwable? = null,
) : ScrapRepository {

    private val details = seeds.associateBy { it.problemId }.toMutableMap()
    private val scrapped = seeds.map { it.problemId }.toMutableSet()

    override suspend fun list(): List<ScrapListItem> {
        listFailWith?.let { throw it }
        return scrapped.mapNotNull { id ->
            details[id]?.let { ScrapListItem(problemId = id, question = it.question, scrappedAt = scrappedAt) }
        }
    }

    override suspend fun get(problemId: Long): ScrapDetail =
        details[problemId] ?: throw NoSuchElementException("스크랩을 찾을 수 없어요: $problemId")

    override suspend fun add(problemId: Long) {
        toggleFailWith?.let { throw it }
        scrapped.add(problemId)
    }

    override suspend fun remove(problemId: Long) {
        toggleFailWith?.let { throw it }
        scrapped.remove(problemId)
    }

    /** 검증용: 해당 문제가 현재 스크랩 상태인지. */
    fun isScrapped(problemId: Long): Boolean = problemId in scrapped
}
