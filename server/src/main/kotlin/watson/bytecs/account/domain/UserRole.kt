package watson.bytecs.account.domain

/**
 * 사용자 유형.
 * GUEST는 가입 없이 학습 상태를 쌓고, 가입 시 같은 식별자를 유지한 채 MEMBER로 승격된다(학습 상태 승계).
 */
enum class UserRole {
    GUEST,
    MEMBER,
}
