package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType

interface ProblemRepository : JpaRepository<Problem, Long> {

    /**
     * id 스냅샷 경로(스크랩·신고)의 승인 게이트. 존재하되 승인 상태가 아니면(초안·검수중·반려·회수) false다 —
     * 미승인 콘텐츠는 신규 노출뿐 아니라 재열람·재접수 경로에서도 노출되지 않는다(수용 기준 15).
     */
    fun existsByIdAndApprovalStatus(id: Long, approvalStatus: ApprovalStatus): Boolean

    /**
     * 스크랩 상세 재열람 전용 승인 게이트. 회수·미승인 문제는 존재해도 조회되지 않아, 호출부가
     * 삭제된 문제와 동일하게(ProblemNotFoundException) 취급할 수 있게 한다.
     */
    fun findByIdAndApprovalStatus(id: Long, approvalStatus: ApprovalStatus): Problem?

    /**
     * 세션 배정·추가 학습 후보를 id 오름차순으로 조회한다(선정은 애플리케이션에서 무작위로 하되, 조회 순서는 고정).
     * 배정에는 id만 필요하므로 전체 엔티티를 로딩하지 않고 id만 프로젝션한다.
     *
     * **서빙 게이트**: 승인(APPROVED) 상태만 후보다(명세 수용 기준 15 — 초안·검수중·반려·회수는 서빙되지 않는다).
     * 이 쿼리가 세션 새 개념 배정·추가 학습 선정·복습 poolIds 가드의 공통 근원이라, 여기의 필터가
     * 신규 노출 경로 전부를 막는다. 이미 배정·저장된 문제는 id 스냅샷으로 로드되므로 필터 대상이 아니다(계획 §4.2).
     */
    @Query("select p.id from Problem p where p.approvalStatus = watson.bytecs.problem.domain.ApprovalStatus.APPROVED order by p.id asc")
    fun findApprovedIdsOrderByIdAsc(): List<Long>

    /**
     * 복습 문제 선정에서 유도형 예외를 가릴 때 쓴다. 문제가 없거나 유형 미상이면 null(둘 다 예외 미적용으로 안전하게 퇴화).
     * 유형만 필요하므로 엔티티를 로딩하지 않고 프로젝션한다.
     */
    @Query("select p.type from Problem p where p.id = :id")
    fun findTypeById(id: Long): ProblemType?

    /**
     * 한 개념에 태깅된 문제 id를 오름차순으로 조회한다(유도형 '아직 안 낸 다른 문제' 후보 선정).
     * @ManyToMany 조인이라 id만 프로젝션해 필요한 것만 가져온다.
     * 새 문제를 꺼내오는 경로이므로 승인(APPROVED) 상태만 후보다(서빙 게이트 — 계획 §4.2).
     */
    @Query(
        "select p.id from Problem p join p.concepts c " +
            "where c.id = :conceptId and p.approvalStatus = watson.bytecs.problem.domain.ApprovalStatus.APPROVED " +
            "order by p.id asc",
    )
    fun findApprovedIdsByConceptIdOrderByIdAsc(conceptId: Long): List<Long>

    /**
     * 카테고리별 학습 이력 조회 전용(N+1 회피, [watson.bytecs.study.application.CategoryHistoryService]).
     * `representativeCategory()`(개념)·`conceptNames()`(개념)·엔리치먼트(+본문 항목, [watson.bytecs.problem.domain.Enrichment.items])를
     * 모두 화면에 실어야 해서, 지연 로딩을 그대로 두면 문제당 최대 3쿼리가 추가로 발생한다(N+1, 코드 리뷰 재검토 발견).
     * concepts·items는 둘 다 [jakarta.persistence.OrderColumn]로 순서를 갖는 인덱스 리스트(bag이 아님)라,
     * 서로 다른 루트(Problem, Enrichment)에 속한 컬렉션 둘을 함께 페치해도 하이버네이트의 MultipleBagFetchException 대상이 아니다.
     * concepts×items 조인으로 같은 문제 행이 늘어날 수 있어 `distinct`로 접는다 — 개념 1~3개·항목 2~4개 수준의 소규모
     * 카테시안 곱이라 허용 범위로 판단한다(카드가 훨씬 커지면 배치 조회로 전환 검토).
     */
    @Query(
        "select distinct p from Problem p " +
            "left join fetch p.concepts " +
            "left join fetch p.enrichment e " +
            "left join fetch e.items " +
            "where p.id in :ids",
    )
    fun findAllByIdWithConceptsAndEnrichment(ids: Collection<Long>): List<Problem>
}
