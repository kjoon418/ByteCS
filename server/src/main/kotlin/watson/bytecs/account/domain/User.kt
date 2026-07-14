package watson.bytecs.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 사용자 애그리거트 루트.
 * 게스트/회원 상태 전이 규칙을 도메인 내부에 캡슐화한다.
 * 게스트→회원 승격은 새 계정을 만들지 않고 같은 식별자(id)를 유지하므로, 그 id에 귀속된 학습 상태가 그대로 승계된다.
 */
@Entity
@Table(name = "users")
class User private constructor(
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: UserRole,

    // 게스트는 이메일이 없다. 회원은 정규화된 이메일을 가지며 전역 유일하다.
    @Column(name = "email", unique = true)
    var email: String?,

    @Column(name = "password_hash")
    var passwordHash: String?,

    @Embedded
    var settings: UserSettings,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    val isMember: Boolean
        get() = role == UserRole.MEMBER

    /**
     * 게스트를 회원으로 승격한다. 같은 id를 유지해 학습 상태를 승계한다.
     * 이미 회원이면 상태 전이 규칙 위반이다.
     */
    fun promoteToMember(email: Email, passwordHash: String) {
        if (role != UserRole.GUEST) {
            throw InvalidUserStateException("이미 회원인 사용자는 다시 가입할 수 없습니다.")
        }
        this.role = UserRole.MEMBER
        this.email = email.value
        this.passwordHash = passwordHash
    }

    fun updateSettings(settings: UserSettings) {
        this.settings = settings
    }

    companion object {
        fun createGuest(): User =
            User(
                role = UserRole.GUEST,
                email = null,
                passwordHash = null,
                settings = UserSettings.default(),
            )

        fun createMember(email: Email, passwordHash: String): User =
            User(
                role = UserRole.MEMBER,
                email = email.value,
                passwordHash = passwordHash,
                settings = UserSettings.default(),
            )
    }
}
