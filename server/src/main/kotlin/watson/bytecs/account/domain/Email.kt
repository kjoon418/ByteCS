package watson.bytecs.account.domain

/**
 * 이메일을 표현하는 VO. 생성 시 트림·소문자화하고 형식을 검증한다.
 */
@ConsistentCopyVisibility
data class Email private constructor(val value: String) {

    companion object {
        const val BLANK_MESSAGE = "이메일은 비어 있을 수 없습니다."
        const val INVALID_FORMAT_MESSAGE = "이메일 형식이 올바르지 않습니다."
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        operator fun invoke(raw: String): Email {
            val normalized = raw.trim().lowercase()
            require(normalized.isNotBlank()) { BLANK_MESSAGE }
            require(EMAIL_PATTERN.matches(normalized)) { INVALID_FORMAT_MESSAGE }

            return Email(normalized)
        }
    }
}
