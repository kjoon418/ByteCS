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

    /**
     * 주어진 개념들 중 **승인된 면접 질문이 있는** 것만, 개념까지 실어(join fetch) 조회한다(정답 시 신규 승급 판정 — DI9).
     * 정답마다 도는 핫패스라 승인 질문 전량([findApproved]) 대신 대상 개념(보통 1~3개)으로 좁힌다. 루브릭은 페치하지 않는다
     * (개념명만 필요 — 지연 로딩을 건드리지 않는다). 빈 목록이면 in 절이 무의미하므로 호출부가 빈 입력을 걸러 부른다.
     */
    @Query(
        "select ip from InterviewPrompt ip " +
            "join fetch ip.concept " +
            "where ip.approvalStatus = watson.bytecs.problem.domain.ApprovalStatus.APPROVED " +
            "and ip.concept.id in :conceptIds",
    )
    fun findApprovedByConceptIdIn(conceptIds: Collection<Long>): List<InterviewPrompt>
}
