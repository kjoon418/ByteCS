package watson.bytecs.account.domain

/**
 * 해싱 전 원문 비밀번호를 표현하는 VO. 최소 길이 규칙을 생성 시 강제한다.
 * 값은 로그·응답에 노출되지 않도록 toString을 재정의한다.
 */
@ConsistentCopyVisibility
data class RawPassword private constructor(val value: String) {

    override fun toString(): String = "RawPassword(****)"

    companion object {
        const val MINIMUM_LENGTH = 8
        const val TOO_SHORT_MESSAGE = "비밀번호는 최소 ${MINIMUM_LENGTH}자 이상이어야 합니다."

        operator fun invoke(raw: String): RawPassword {
            require(raw.length >= MINIMUM_LENGTH) { TOO_SHORT_MESSAGE }

            return RawPassword(raw)
        }
    }
}
