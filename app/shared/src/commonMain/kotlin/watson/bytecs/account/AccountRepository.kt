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

    /** 본인 계정 삭제. `DELETE /api/users/me`(인증 필요). */
    suspend fun deleteMe()
}
