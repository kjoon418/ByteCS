package watson.bytecs.extrastudy.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.extrastudy.domain.ExtraStudy

interface ExtraStudyRepository : JpaRepository<ExtraStudy, Long> {

    /** 사용자당 1행 get-or-create의 조회 축. user_id 유니크 제약과 짝을 이룬다. */
    fun findByUserId(userId: Long): ExtraStudy?

    /**
     * 사용자가 추가 학습에서 '정답으로 통과한' 본 문제 id들. 세션 solved와 합쳐 '이미 푼 문제' 풀을 이룬다(LearningHistory).
     */
    @Query("select p from ExtraStudy e join e.mutableSolvedProblemIds p where e.userId = :userId")
    fun findSolvedProblemIds(userId: Long): List<Long>

    /**
     * 지금 열린(이어 풀) 항목의 문제 id. 열린 항목이 없으면 null.
     * '배정 이력'(만난 적 있는 문제)에 편입하기 위해 solved와 별도로 합친다 — 열린 문제도 이미 만난 문제이기 때문이다.
     */
    @Query("select e.openItem.problemId from ExtraStudy e where e.userId = :userId")
    fun findOpenProblemId(userId: Long): Long?

    /**
     * 계정 삭제 시 그 사용자의 추가 학습을 삭제한다(학습 상태 삭제 흐름 편입).
     * 반드시 파생 쿼리(엔티티 로드 후 삭제)여야 한다 — solvedProblemIds는 @ElementCollection이라
     * 벌크 JPQL delete로는 컬렉션 테이블(extra_study_solved)이 정리되지 않아 고아 행을 남긴다(SessionRepository.deleteByUserId 참고).
     */
    fun deleteByUserId(userId: Long)
}
