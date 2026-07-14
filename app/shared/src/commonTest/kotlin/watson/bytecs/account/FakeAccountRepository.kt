package watson.bytecs.account

/**
 * 결정적 인메모리 [AccountRepository]. 서버를 흉내 내되(게스트 발급→제자리 승격→조회), 오류를 주입해
 * SessionManager·뷰모델의 상태 전이를 네트워크 없이 검증한다.
 */
class FakeAccountRepository(
    var current: Account? = null,
) : AccountRepository {

    private var idCounter = 0L

    /** 호출 이력(순서·횟수 단언용). */
    val calls = mutableListOf<String>()

    var issueGuestError: Throwable? = null
    var registerError: Throwable? = null
    var loginError: Throwable? = null
    var getMeError: Throwable? = null
    var updateSettingsError: Throwable? = null
    var deleteMeError: Throwable? = null

    var deleteMeCount = 0
    var lastUpdatedSize: Int? = null

    override suspend fun issueGuest(): GuestSession {
        calls += "issueGuest"
        issueGuestError?.let { throw it }
        val id = ++idCounter
        current = Account(userId = id, role = Role.GUEST, email = null, dailySessionSize = 5)
        return GuestSession(token = "guest-$id", userId = id, role = Role.GUEST)
    }

    override suspend fun register(email: String, password: String): AuthSession {
        calls += "register"
        registerError?.let { throw it }
        // 게스트가 있으면 같은 id를 유지해 제자리 승격(학습 상태 승계)을 재현한다.
        val existing = current
        val id = existing?.userId ?: ++idCounter
        current = Account(
            userId = id,
            role = Role.MEMBER,
            email = email,
            dailySessionSize = existing?.dailySessionSize ?: 5,
        )
        return AuthSession(token = "member-$id")
    }

    override suspend fun login(email: String, password: String): AuthSession {
        calls += "login"
        loginError?.let { throw it }
        val id = ++idCounter
        current = Account(userId = id, role = Role.MEMBER, email = email, dailySessionSize = 5)
        return AuthSession(token = "member-$id")
    }

    override suspend fun getMe(): Account {
        calls += "getMe"
        getMeError?.let { throw it }
        return current ?: throw IllegalStateException("no current user")
    }

    override suspend fun updateSettings(dailySessionSize: Int): Account {
        calls += "updateSettings"
        updateSettingsError?.let { throw it }
        val account = current ?: throw IllegalStateException("no current user")
        lastUpdatedSize = dailySessionSize
        current = account.copy(dailySessionSize = dailySessionSize)
        return current!!
    }

    override suspend fun deleteMe() {
        calls += "deleteMe"
        deleteMeError?.let { throw it }
        deleteMeCount++
        current = null
    }
}
