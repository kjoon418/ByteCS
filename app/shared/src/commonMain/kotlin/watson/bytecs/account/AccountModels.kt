package watson.bytecs.account

/**
 * 계정 슬라이스의 도메인 모델. 백엔드 DTO(유선 형태)와 분리해, API가 바뀌어도 매핑 한곳만 고치면 되게 한다.
 * UI·SessionManager는 이 모델만 본다(DTO 비노출).
 */

/** 사용자 역할. 서버 enum(GUEST/MEMBER)과 문자열로 대조한다. */
enum class Role {
    GUEST,
    MEMBER,
    ;

    companion object {
        /** 서버가 준 역할 문자열을 대소문자 무시로 매핑한다. 미지값은 게스트로 보수적으로 처리(권한 축소). */
        fun from(raw: String): Role =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: GUEST
    }
}

/**
 * 로그인한(또는 게스트) 사용자 스냅샷.
 * 게스트는 이메일이 없으므로 [email]은 nullable이다.
 */
data class Account(
    val userId: Long,
    val role: Role,
    val email: String?,
    val dailySessionSize: Int,
) {
    val isMember: Boolean get() = role == Role.MEMBER
}

/** 게스트 발급 결과. 발급 토큰과 신원을 함께 받아 즉시 인증 상태를 구성한다. */
data class GuestSession(
    val token: String,
    val userId: Long,
    val role: Role,
)

/** 로그인·가입 성공 결과. 서버는 토큰만 돌려주므로, 상세 프로필은 이후 getMe로 채운다. */
data class AuthSession(
    val token: String,
)

/**
 * 가입 시 이메일이 이미 사용 중(서버 409 EMAIL_DUPLICATED). 사용자 언어로 안내하되 비난하지 않는다.
 * 계정 열거와 무관한 정상 검증 실패라 로그인 실패와 달리 명확히 구분해 노출한다.
 */
class EmailAlreadyInUseException : Exception("이미 사용 중인 이메일이에요")

/**
 * 로그인 실패(서버 401 INVALID_CREDENTIALS). ⭐️ 이메일 없음과 비밀번호 불일치를 구분하지 않는다
 * (계정 열거 방지 — 서버가 동일 응답을 주며, 클라이언트도 동일 메시지로 처리).
 */
class InvalidCredentialsException : Exception("이메일 또는 비밀번호를 다시 확인해 주세요")

/**
 * 요청 입력이 서버 검증에 걸림(서버 400 INVALID_INPUT). 예: 이메일 형식 오류.
 * 클라 사전 검증을 어떻게든 통과해 서버까지 간 경우를 위한 폴백이라, 서버가 돌려준 메시지를 그대로 실어
 * 사용자 언어로 안내한다(연결 실패로 오인되지 않게).
 */
class InvalidInputException(message: String) : Exception(message)
