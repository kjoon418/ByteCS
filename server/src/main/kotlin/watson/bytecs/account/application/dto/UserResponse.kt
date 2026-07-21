package watson.bytecs.account.application.dto

/**
 * 사용자 조회·설정 변경 응답.
 * 게스트는 이메일이 없으므로 email은 nullable이며, 비밀번호 해시 등 민감 정보는 노출하지 않는다.
 * preferredDifficulty는 선호 난이도(Difficulty enum 이름)이며, 미설정이면 null(설정 화면이 '자동'으로 표시).
 */
data class UserResponse(
    val userId: Long,
    val role: String,
    val email: String?,
    val dailySessionSize: Int,
    val preferredDifficulty: String?,
)
