package watson.bytecs.interview.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.interview.domain.InterviewSession
import java.time.LocalDate

interface InterviewSessionRepository : JpaRepository<InterviewSession, Long> {

    /** '오늘의 면접 세션' 조회 축 — 그 날짜의 가장 최근 세션(id 내림차순 첫 행). 일반 세션과 같은 관례. */
    fun findTopByUserIdAndSessionDateOrderByIdDesc(userId: Long, sessionDate: LocalDate): InterviewSession?

    /**
     * 그 날짜에 채점 성공(judged=true) 칸을 하나라도 포함한 세션 수(하루 쿼터 차감 기준, 계획 §3.3).
     * 전량 폴백으로 끝난 세션은 세지 않는다 — 그런 세션만 있으면 사용자가 오늘 다시 시작할 수 있다.
     */
    @Query(
        "select count(distinct s) from InterviewSession s join s.mutableItems item " +
            "where s.userId = :userId and s.sessionDate = :date and item.judged = true",
    )
    fun countGradedSessionsOn(userId: Long, date: LocalDate): Long

    /**
     * 계정 삭제 시 그 사용자의 면접 세션을 일괄 삭제한다. items가 @ElementCollection이라
     * 반드시 파생 쿼리여야 한다(벌크 JPQL delete는 컬렉션 테이블을 정리하지 않아 고아 행을 남긴다 — SessionRepository 관례).
     */
    fun deleteByUserId(userId: Long)
}
