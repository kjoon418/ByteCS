package watson.bytecs.session.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.session.domain.Session
import java.time.Instant
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
     * 사용자가 '이 세션이 아닌' 다른 세션에서 정답으로 통과한 본 문제 id들(중복 제거).
     * 연결 문제 잠금 해제 계산(D2)에서 '이 세션에서 처음 만난 개념'을 가리는 기준 — 다른 세션에서 이미 푼 문제(그 개념)는
     * 이번에 처음 만난 것이 아니다(복습·D8 재출제로 이 세션에 다시 나와도 마찬가지, s.id 제외로 배제된다).
     */
    @Query(
        "select distinct item.problemId from Session s join s.mutableItems item " +
            "where s.userId = :userId and item.solved = true and s.id <> :sessionId",
    )
    fun findSolvedProblemIdsExcludingSession(userId: Long, sessionId: Long): List<Long>

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

    /**
     * 지표 1 — 풀이 화면에 진입한 적 있는(started_at 기록) DISTINCT 사용자 수.
     * 세션 생성(배정)만으로는 started_at이 남지 않으므로, '시작하기'로 풀이 화면에 들어온 사용자만 센다.
     */
    @Query("select count(distinct s.userId) from Session s where s.startedAt is not null")
    fun countUsersStarted(): Long

    /** 지표 2 — 세션(오늘의 한입)을 완료한 적 있는(status=COMPLETED) DISTINCT 사용자 수. */
    @Query(
        "select count(distinct s.userId) from Session s " +
            "where s.status = watson.bytecs.session.domain.SessionStatus.COMPLETED",
    )
    fun countUsersCompleted(): Long

    /**
     * 지표 3 — 세션 완료 후 추가로 문제를 더 푼 DISTINCT 사용자 수.
     * 같은 (user_id, session_date) 안에서, 완료된 세션(completed)보다 나중에 만들어진 세션(later.id > completed.id)의
     * 풀이 화면까지 진입한(later.startedAt is not null) 경우를 센다 — '조금 더 풀기'로 재진입해 실제로 풀기 시작한 사용자.
     * 같은 사용자가 여러 날 그랬어도 distinct로 1명이다.
     */
    @Query(
        "select count(distinct later.userId) from Session completed, Session later " +
            "where later.userId = completed.userId and later.sessionDate = completed.sessionDate " +
            "and later.id > completed.id " +
            "and completed.status = watson.bytecs.session.domain.SessionStatus.COMPLETED " +
            "and later.startedAt is not null",
    )
    fun countUsersStudiedMoreAfterCompletion(): Long

    // ── 기간 한정판(관리자 페이지 기간 집계) — 각 지표의 이벤트 시각([from, to) 반개구간)으로 거른다. ──

    /** 지표 1(기간) — startedAt이 [from, to) 안에 든 DISTINCT 사용자 수. */
    @Query("select count(distinct s.userId) from Session s where s.startedAt >= :from and s.startedAt < :to")
    fun countUsersStartedBetween(@Param("from") from: Instant, @Param("to") to: Instant): Long

    /**
     * 지표 2(기간) — completedAt이 [from, to) 안에 든 DISTINCT 사용자 수.
     * 전체 기간판은 status로 세지만(완료 시각 도입 전 행 포함), 기간판은 완료 시각([completedAt], V6)으로 거른다.
     */
    @Query("select count(distinct s.userId) from Session s where s.completedAt >= :from and s.completedAt < :to")
    fun countUsersCompletedBetween(@Param("from") from: Instant, @Param("to") to: Instant): Long

    /** 지표 3(기간) — '조금 더 풀기' 재진입(later.startedAt)이 [from, to) 안에 든 DISTINCT 사용자 수. */
    @Query(
        "select count(distinct later.userId) from Session completed, Session later " +
            "where later.userId = completed.userId and later.sessionDate = completed.sessionDate " +
            "and later.id > completed.id " +
            "and completed.status = watson.bytecs.session.domain.SessionStatus.COMPLETED " +
            "and later.startedAt >= :from and later.startedAt < :to",
    )
    fun countUsersStudiedMoreAfterCompletionBetween(@Param("from") from: Instant, @Param("to") to: Instant): Long

    /**
     * 관리자 지표 ② — 난이도별 '정답 공개(포기)율'의 원자료(2차 적응 조정 임계값 검증 근거).
     * 분모는 **푼 문제(solved=true)** — 실제로 완결한 풀이만 신호로 본다(미도달 미래 칸이 분모를 희석하지 않게).
     * 분자는 그중 정답을 열어 본(revealed=true) 칸이다. 세션 칸(problemId)과 문제(Problem.difficulty)는 연관이 없어
     * theta 조인(p.id = item.problemId)으로 잇는다. 난이도별로 (푼 수, 공개 수)를 집계하고 비율은 서비스가 계산한다.
     */
    @Query(
        "select p.difficulty as difficulty, " +
            "count(item.problemId) as solvedCount, " +
            "sum(case when item.revealed = true then 1L else 0L end) as revealedCount " +
            "from Session s join s.mutableItems item, Problem p " +
            "where item.solved = true and p.id = item.problemId " +
            "group by p.difficulty",
    )
    fun countSolvedItemRevealsByDifficulty(): List<DifficultyRevealCount>

    /** JPQL 별칭 프로젝션 — [findSolvedItemAnswers] 전용. */
    interface SolvedItemAnswer {
        val problemId: Long
        val submittedAnswer: String?
    }

    /** JPQL 별칭 프로젝션 — [countSolvedItemRevealsByDifficulty] 전용(난이도별 푼 수·정답 공개 수). */
    interface DifficultyRevealCount {
        val difficulty: Difficulty?
        val solvedCount: Long
        val revealedCount: Long
    }
}
