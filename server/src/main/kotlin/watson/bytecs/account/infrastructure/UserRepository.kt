package watson.bytecs.account.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.account.domain.User

interface UserRepository : JpaRepository<User, Long> {

    /** 로그인·중복 가입 검증은 정규화된 이메일 문자열로 조회한다. */
    fun findByEmail(email: String): User?
}
