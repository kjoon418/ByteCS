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
import watson.bytecs.problem.domain.Difficulty
import java.time.LocalDate

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

    @Embedded
    var streak: StudyStreak,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    // 완료 화면 난이도 제안에 응답(선택 또는 거절)했는지. 설정값이 아니라 '제안 노출 상태'라 UserSettings가 아닌 User 직속 필드로 둔다.
    // 게스트→회원 승격은 같은 id를 in-place 승격하므로 이 값도 자동으로 승계된다(별도 이관 없음).
    @Column(name = "difficulty_prompt_done", nullable = false)
    var difficultyPromptDone: Boolean = false
        protected set

    val isMember: Boolean
        get() = role == UserRole.MEMBER

    /**
     * 오늘 학습(세션 완료)을 스트릭에 반영한다.
     * 갱신 규칙(연속/재시작)은 [StudyStreak]에 위임하고, 애그리거트는 상태 소유만 책임진다.
     */
    fun recordStudy(today: LocalDate) {
        this.streak = streak.record(today)
    }

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

    /** 일일 세션 분량만 바꾼다. 선호 난이도 등 다른 설정은 보존한다(부분 갱신 — 설정 하나를 바꿔도 나머지를 초기화하지 않는다). */
    fun updateDailySessionSize(dailySessionSize: Int) {
        this.settings = settings.copy(dailySessionSize = dailySessionSize)
    }

    /**
     * 선호 난이도를 지정한다(가중 출제의 입력). 직접 지정한 사용자는 이미 자기 난이도를 아는 것으로 보아
     * 완료 화면 제안 노출을 종료한다(promptDone=true — 이미 아는 사용자에게 다시 제안하지 않는다).
     */
    fun updatePreferredDifficulty(preferredDifficulty: Difficulty) {
        this.settings = settings.copy(preferredDifficulty = preferredDifficulty)
        this.difficultyPromptDone = true
    }

    /** 완료 화면 난이도 제안에 응답(거절 포함)했음을 기록한다. 어느 쪽이든 1회 응답하면 다시 묻지 않는다(잔소리 금지). */
    fun markDifficultyPromptDone() {
        this.difficultyPromptDone = true
    }

    /**
     * 완료 화면에서 난이도 제안을 노출해야 하는지 판단한다(선호 미설정 && 아직 미응답).
     * 클라이언트가 노출 조건을 재계산하지 않게 서버가 단일 출처로 판단한다(계획 §3.3·§4.2).
     */
    fun needsDifficultyPrompt(): Boolean =
        settings.preferredDifficulty == null && !difficultyPromptDone

    companion object {
        fun createGuest(): User =
            User(
                role = UserRole.GUEST,
                email = null,
                passwordHash = null,
                settings = UserSettings.default(),
                streak = StudyStreak.initial(),
            )

        fun createMember(email: Email, passwordHash: String): User =
            User(
                role = UserRole.MEMBER,
                email = email.value,
                passwordHash = passwordHash,
                settings = UserSettings.default(),
                streak = StudyStreak.initial(),
            )

        /** 관리자 계정. 자가 가입 경로가 없으며 기동 부트스트랩에서만 생성된다(관리자 페이지 폼 로그인 전용). */
        fun createAdmin(email: Email, passwordHash: String): User =
            User(
                role = UserRole.ADMIN,
                email = email.value,
                passwordHash = passwordHash,
                settings = UserSettings.default(),
                streak = StudyStreak.initial(),
            )
    }
}
