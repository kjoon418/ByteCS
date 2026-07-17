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

    /**
     * 사용자가 세션에서 정답으로 통과한 본 문제의 (id, 그때 제출한 정답) 쌍(카테고리별 학습 이력의 '내 답' 복원용).
     * 세션 날짜 오름차순으로 정렬해, 같은 문제를 복습으로 여러 날 다시 풀었다면 호출부가 마지막 통과 값으로 덮어써 최신 제출을 취하게 한다.
     * 추가 학습은 열린 항목이 solved로 승격되며 제출 답을 보존하지 않으므로([ExtraStudyItem]), 이 쌍은 세션 출처만 담당한다 — 그 결손은 카테고리별 이력 응답에서 null로 graceful 처리한다.
     */
    @Query(
        "select item.problemId as problemId, item.submittedAnswer as submittedAnswer from Session s join s.mutableItems item " +
            "where s.userId = :userId and item.solved = true order by s.sessionDate asc",
    )
    fun findSolvedItemAnswers(userId: Long): List<SolvedItemAnswer>

    /** JPQL 별칭 프로젝션 — [findSolvedItemAnswers] 전용. */
    interface SolvedItemAnswer {
        val problemId: Long
        val submittedAnswer: String?
    }
}
