package watson.bytecs.session.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.session.domain.Session
import java.time.LocalDate

interface SessionRepository : JpaRepository<Session, Long> {

    /**
     * '오늘의 세션' 조회 축. 하루에 여러 세션이 있을 수 있으므로(D6·D9 일원화),
     * 그 날짜의 가장 최근 세션(id 내림차순 첫 행)을 오늘의 세션으로 본다.
     * 없으면 null — 서비스가 새 세션을 만든다('조금 더 풀기'도 이 최신 세션의 완료 여부로 새 세션 여부를 정한다).
     */
    fun findTopByUserIdAndSessionDateOrderByIdDesc(userId: Long, sessionDate: LocalDate): Session?

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
     * 이제 모든 풀이는 세션에서 나오므로(D6·D9 일원화) 이 쌍이 통과한 문제의 '내 답'을 전부 담는다.
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
