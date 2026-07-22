package watson.bytecs.interview.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.interview.domain.InterviewPrompt

interface InterviewPromptRepository : JpaRepository<InterviewPrompt, Long> {

    /**
     * 서빙 게이트: **승인(APPROVED)된 면접 질문만** 조회한다(계획 §3.3 — APPROVED만 서빙).
     * 초안·검수중·반려·회수는 서빙 후보가 아니다. 개념까지 함께 실어(join fetch) 루브릭 대조 채점·화면 표시가
     * 지연 로딩 없이 쓰게 한다. @OrderColumn 컬렉션 하나(rubricPoints)만 페치하므로 MultipleBagFetch 대상이 아니다.
     */
    @Query(
        "select distinct ip from InterviewPrompt ip " +
            "join fetch ip.concept " +
            "left join fetch ip.rubricPoints " +
            "where ip.approvalStatus = watson.bytecs.problem.domain.ApprovalStatus.APPROVED",
    )
    fun findApproved(): List<InterviewPrompt>
}
