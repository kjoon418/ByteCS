package watson.bytecs.session.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.session.domain.Session
import java.time.LocalDate

interface SessionRepository : JpaRepository<Session, Long> {

    /** 하루 1세션 get-or-create의 조회 축. (user_id, session_date) 유니크 제약과 짝을 이룬다. */
    fun findByUserIdAndSessionDate(userId: Long, sessionDate: LocalDate): Session?

    /**
     * 사용자가 지금까지 어떤 세션에서든 '정답으로 통과한' 본 문제 id들(중복 제거).
     * '새 개념만 배정'(MVP)에서 이미 푼 문제를 제외하는 기준이다. 아직 못 푼 배정 문제는 다시 배정될 수 있다.
     */
    @Query(
        "select distinct item.problemId from Session s join s.mutableItems item " +
            "where s.userId = :userId and item.solved = true",
    )
    fun findSolvedProblemIds(userId: Long): List<Long>
}
