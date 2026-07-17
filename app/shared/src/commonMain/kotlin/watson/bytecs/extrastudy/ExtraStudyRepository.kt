package watson.bytecs.extrastudy

/**
 * 추가 학습 데이터 접근 계약. 선정·판정·상태 전이를 서버에 위임한다(클라이언트는 상태를 그리기만 한다).
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접 다루지 않는다.
 */
interface ExtraStudyRepository {
    /**
     * 현재(이어 풀) 문제를 가져오거나, 없으면 새로 뽑아 열린 항목으로 고정해 받는다. 뽑을 게 없으면 소진.
     * `GET /api/extra-study/current`.
     */
    suspend fun getCurrent(): ExtraStudyState

    /** 현재 열린 문제에 답을 제출하고 결과를 받는다. `POST /api/extra-study/attempts`. */
    suspend fun submitAttempt(answer: String): ExtraStudyAttempt

    /** 현재 열린 문제의 모범답안을 공개한다(안전판). `POST /api/extra-study/reveal`. */
    suspend fun reveal(): ExtraStudyReveal

    /**
     * 다음 힌트 하나를 연다(약→강, pull). `POST /api/extra-study/hints/reveal`.
     * [revealedCount]는 클라가 아는 현재 공개 수 — 서버는 일치할 때만 +1 하고, 공개된 전체 목록을 돌려준다(서버가 원천).
     * ⚠️ 정답 공개인 [reveal]와 다르다(이름 혼동 주의): 이쪽은 힌트, 저쪽은 모범답안.
     */
    suspend fun revealHint(revealedCount: Int): ExtraStudyHintReveal
}
