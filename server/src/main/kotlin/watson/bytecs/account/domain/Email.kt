package watson.bytecs.account.domain

/**
 * 이메일을 표현하는 VO. 생성 시 트림·소문자화하고 형식을 검증한다.
 */
@ConsistentCopyVisibility
data class Email private constructor(val value: String) {

    companion object {
        const val BLANK_MESSAGE = "이메일은 비어 있을 수 없습니다."
        const val INVALID_FORMAT_MESSAGE = "이메일 형식이 올바르지 않습니다."

        // ⚠️ 동기화 지점: 서버·클라(app 모듈)는 코드 공유가 없어 정규식이 중복된다.
        // 클라 `AuthUiState.EMAIL_PATTERN`(app/shared/src/commonMain/kotlin/watson/bytecs/account/AuthViewModel.kt)과
        // 반드시 동일하게 유지할 것 — 어긋나면 클라 사전 검증을 통과한 입력이 서버에서 거절되거나 그 반대가 된다.
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        operator fun invoke(raw: String): Email {
            val normalized = raw.trim().lowercase()
            require(normalized.isNotBlank()) { BLANK_MESSAGE }
            require(EMAIL_PATTERN.matches(normalized)) { INVALID_FORMAT_MESSAGE }

            return Email(normalized)
        }
    }
}
