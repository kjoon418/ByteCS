package watson.bytecs.session

/**
 * 일일 세션 데이터 접근 계약. 배정·진행·정답 공개·지난 문제를 서버에 위임한다(판정·상태 전이는 전적으로 서버).
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접 다루지 않는다.
 */
interface SessionRepository {
    /** 오늘의 세션을 가져오거나(없으면) 새로 배정해 시작 상태를 받는다. `GET /api/sessions/today`. */
    suspend fun getToday(): DailySession

    /**
     * '조금 더 풀기' — 오늘 최신 세션이 완료 상태면 새 세션을 만들어 재진입하고, 아직 진행 중이면 그 세션을
     * 그대로 받는다(멱등, 항상 200). `POST /api/sessions/today/next`. 추가 학습(폐지, D6·D9)을 대체한다 —
     * '조금 더 풀기'는 이제 별도 화면이 아니라 새 세션으로 같은 풀이 흐름에 재진입하는 것이다.
     */
    suspend fun startNextSession(): DailySession

    /**
     * 풀이 화면 진입을 서버에 표시한다(테스터 지표 수집). `POST /api/sessions/today/start`.
     * 오늘 최신 세션의 시작 시각을 최초 1회 기록한다(멱등). 부수 효과이므로 응답 본문은 없고, 실패해도 풀이 흐름을 막지 않는다.
     */
    suspend fun markStarted()

    /** 현재 본 문제에 답을 제출하고 결과를 받는다. `POST /api/sessions/today/attempts`. */
    suspend fun submitAttempt(answer: String): AttemptOutcome

    /** 현재 본 문제의 모범답안을 공개한다(안전판). `POST /api/sessions/today/reveal`. */
    suspend fun reveal(): Reveal

    /**
     * 다음 힌트 하나를 연다(약→강, pull). `POST /api/sessions/today/hints/reveal`.
     * [revealedCount]는 클라가 아는 현재 공개 수 — 서버는 일치할 때만 +1 하고, 공개된 전체 목록을 돌려준다(서버가 원천).
     * ⚠️ 정답 공개인 [reveal]와 다르다(이름 혼동 주의): 이쪽은 힌트, 저쪽은 모범답안.
     */
    suspend fun revealHint(revealedCount: Int): HintReveal

    /** 이미 지나온 본 문제를 읽기 전용으로 조회한다. `GET /api/sessions/today/items/{position}`. */
    suspend fun getPastItem(position: Int): PastItem
}
