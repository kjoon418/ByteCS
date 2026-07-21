package watson.bytecs.account.infrastructure

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import watson.bytecs.account.domain.User
import watson.bytecs.problem.domain.Difficulty
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {

    /** 로그인·중복 가입 검증은 정규화된 이메일 문자열로 조회한다. */
    fun findByEmail(email: String): User?

    /**
     * 관리자 지표 ① — 학습자(게스트·회원, ADMIN 제외)의 선호 난이도 분포. 미설정(null)도 한 그룹으로 잡힌다.
     * 운영 주체(ADMIN)는 학습자가 아니라 분모에서 제외해야 '미설정' 버킷이 부풀지 않는다.
     */
    @Query(
        "select u.settings.preferredDifficulty as difficulty, count(u) as count from User u " +
            "where u.role <> watson.bytecs.account.domain.UserRole.ADMIN " +
            "group by u.settings.preferredDifficulty",
    )
    fun countLearnersByPreferredDifficulty(): List<PreferredDifficultyCount>

    /** JPQL 별칭 프로젝션 — [countLearnersByPreferredDifficulty] 전용(난이도·인원, 난이도 null=미설정). */
    interface PreferredDifficultyCount {
        val difficulty: Difficulty?
        val count: Long
    }

    /**
     * 사용자 행에 비관적 쓰기 잠금(SELECT … FOR UPDATE)을 걸어 조회한다(M1).
     * 같은 사용자의 '오늘 세션 생성'(조회-후-생성)을 사용자 행 단위로 직렬화하는 데 쓴다 —
     * 두 동시 요청이 같은 사용자 행을 잡으면 뒤 요청은 앞 요청의 트랜잭션이 커밋될 때까지 대기하다가,
     * 앞 요청이 만든 세션을 조회하게 되어 세션 중복 생성을 막는다(H2·PostgreSQL 공통).
     * 생성 경합에만 필요하므로 조회 전용 경로(제출·공개·지난 문제)는 이 잠금을 쓰지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): Optional<User>
}
