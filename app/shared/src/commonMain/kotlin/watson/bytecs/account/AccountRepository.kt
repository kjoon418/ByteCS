package watson.bytecs.account

/**
 * 계정 데이터 접근 계약. 발급·가입·로그인·조회·설정·탈퇴를 서버에 위임한다.
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로, 저장소는 토큰을 직접 다루지 않는다.
 */
interface AccountRepository {
    /** 게스트 계정과 토큰을 발급받는다. `POST /api/guests`. */
    suspend fun issueGuest(): GuestSession

    /**
     * 회원 가입. 현재 게스트 토큰이 헤더에 실려 있으면 서버가 그 게스트를 제자리 승격해 학습 기록을 승계한다.
     * `POST /api/auth/register`. 이메일 중복은 [EmailAlreadyInUseException]로 올린다.
     */
    suspend fun register(email: String, password: String): AuthSession

    /** 로그인. `POST /api/auth/login`. 실패는 [InvalidCredentialsException]로 올린다(원인 구분 없음). */
    suspend fun login(email: String, password: String): AuthSession

    /** 본인 조회. `GET /api/users/me`(인증 필요). */
    suspend fun getMe(): Account

    /** 학습 설정(세션 크기) 변경. `PATCH /api/users/me/settings`(인증 필요). */
    suspend fun updateSettings(dailySessionSize: Int): Account

    /**
     * 선호 난이도 변경. `PATCH /api/users/me/settings`(인증 필요, 부분 갱신 — preferredDifficulty만 보낸다).
     * 직접 지정하면 서버가 세션 완료 화면 제안(Stage 5)도 함께 종료한다(도메인 규칙, 클라이언트가 신경 쓸 것 없음).
     * [PreferredDifficulty]는 null을 표현하지 못하므로, 이 메서드로 선호를 다시 미설정으로 되돌릴 수는 없다.
     */
    suspend fun updatePreferredDifficulty(value: PreferredDifficulty): Account

    /**
     * 세션 완료 화면의 난이도 제안 카드에서 "지금은 괜찮아요"(거절)를 눌렀을 때 기록한다.
     * `PATCH /api/users/me/settings`(인증 필요, 부분 갱신 — `difficultyPromptDone`만 보낸다).
     * 선호는 바꾸지 않는다 — 응답했다는 사실만 남겨, 서버가 이후 완료 화면에서 다시 제안하지 않게 한다.
     */
    suspend fun dismissDifficultyPrompt(): Account

    /** 본인 계정 삭제. `DELETE /api/users/me`(인증 필요). */
    suspend fun deleteMe()
}
