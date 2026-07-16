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

    /**
     * 사용자가 지금까지 어떤 세션에서든 '배정받은'(풀었든 아니든) 본 문제 id들(중복 제거).
     * 유도형 복습 예외에서 '아직 배정된 적 없는 다른 문제'를 가리는 기준이다.
     */
    @Query(
        "select distinct item.problemId from Session s join s.mutableItems item " +
            "where s.userId = :userId",
    )
    fun findAssignedProblemIds(userId: Long): List<Long>

    /**
     * 계정 삭제 시 그 사용자의 세션을 일괄 삭제한다(학습 상태 삭제 흐름 편입).
     * 반드시 파생 쿼리(엔티티 단위 로드 후 삭제)여야 한다 — items는 @ElementCollection이라
     * 벌크 JPQL delete(@Query/@Modifying)로는 컬렉션 테이블(study_session_item)이 정리되지 않아
     * 고아 행을 새로 만든다. 파생 delete는 각 Session을 로드해 지우므로 컬렉션 행까지 함께 삭제된다.
     */
    fun deleteByUserId(userId: Long)
}
