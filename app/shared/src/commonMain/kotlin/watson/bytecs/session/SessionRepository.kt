package watson.bytecs.session

/**
 * 일일 세션 데이터 접근 계약. 배정·진행·정답 공개·지난 문제를 서버에 위임한다(판정·상태 전이는 전적으로 서버).
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접 다루지 않는다.
 */
interface SessionRepository {
    /** 오늘의 세션을 가져오거나(없으면) 새로 배정해 시작 상태를 받는다. `GET /api/sessions/today`. */
    suspend fun getToday(): DailySession

    /** 현재 본 문제에 답을 제출하고 결과를 받는다. `POST /api/sessions/today/attempts`. */
    suspend fun submitAttempt(answer: String): AttemptOutcome

    /** 현재 본 문제의 모범답안을 공개한다(안전판). `POST /api/sessions/today/reveal`. */
    suspend fun reveal(): Reveal

    /** 이미 지나온 본 문제를 읽기 전용으로 조회한다. `GET /api/sessions/today/items/{position}`. */
    suspend fun getPastItem(position: Int): PastItem
}
