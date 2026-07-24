package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Difficulty
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

    /**
     * 난이도 가중 출제([watson.bytecs.session.application.DifficultyWeightedShuffler])가 후보별 난이도를 알아야 해서,
     * 승인된 후보 id의 (id, 난이도)만 프로젝션한다(엔티티 전체 로딩 회피). 난이도 미상(null)도 그대로 실어
     * 셔플러가 '가장 먼 거리'로 취급하게 한다. 입력 ids는 이미 승인 게이트를 통과한 후보지만,
     * 다른 서빙 쿼리와 동일하게 승인(APPROVED) 필터를 명시해 재확인한다.
     */
    @Query(
        "select p.id as id, p.difficulty as difficulty from Problem p " +
            "where p.id in :ids and p.approvalStatus = watson.bytecs.problem.domain.ApprovalStatus.APPROVED",
    )
    fun findApprovedDifficultiesByIdIn(ids: Collection<Long>): List<ProblemDifficultyView>

    /** JPQL 별칭 프로젝션 — [findApprovedDifficultiesByIdIn] 전용(가중 출제용 id·난이도 쌍). */
    interface ProblemDifficultyView {
        val id: Long
        val difficulty: Difficulty?
    }

    /**
     * 후보 중 **연결 문제(integration=true)**의 구성 개념 id만 (문제 id, 개념 id) 쌍으로 한 번에 조회한다
     * (연결 문제 하드 게이트, 계획 §3.2 · DI12). 문제당 개별 조회(N+1)를 피하려고 후보 id 집합을 한 쿼리로 펼쳐,
     * 애플리케이션([watson.bytecs.session.application.SessionCreator])이 문제별로 접어 '전 개념 학습' 게이트를 판정한다.
     * 결과에 없는 문제(미지정 = 개념 수 무관)는 게이트 밖이라 그대로 통과한다 — DI12로 판별을 태깅 수에서 명시 플래그로 옮긴 핵심.
     * @ManyToMany 조인이라 id만 프로젝션한다. 입력 ids는 이미 승인 게이트([findApprovedIdsOrderByIdAsc])를 통과한 후보다.
     */
    @Query(
        "select p.id as problemId, c.id as conceptId from Problem p join p.concepts c " +
            "where p.id in :ids and p.integration = true",
    )
    fun findConceptIdsOfIntegrationProblems(ids: Collection<Long>): List<ProblemConceptView>

    /**
     * 후보 문제별 구성 개념 id를 (문제 id, 개념 id) 쌍으로 한 번에 조회한다(연결 문제 잠금 해제 계산 D2).
     * 이 세션·다른 세션에서 푼 문제들의 개념을 배치로 모아 '이 세션에서 처음 만난 개념'을 판정할 때 쓴다.
     * 승인 여부와 무관하게 조회한다 — 이미 푼 문제는 학습 이력이라(회수됐어도 '만난 적'은 사실) 개념 귀속이 유효하다.
     */
    @Query("select p.id as problemId, c.id as conceptId from Problem p join p.concepts c where p.id in :ids")
    fun findConceptIdsByProblemIdIn(ids: Collection<Long>): List<ProblemConceptView>

    /**
     * 승인된 **지정 연결 문제(integration=true)**를 구성 개념까지 함께 로드한다(연결 문제 잠금 해제 계산 D2).
     * 개념을 join fetch해 [watson.bytecs.problem.domain.Problem.conceptIds]·conceptNames를 태깅 순서대로 쓸 수 있게 한다(N+1 없이 1쿼리).
     * 지정 연결 문제는 소수의 큐레이션 콘텐츠라 전량 로드해도 비용이 유계다. 미승인(초안·회수 등)은 서빙되지 않으므로 제외한다
     * (열려도 만날 수 없는 문제를 축하하지 않는다). @OrderColumn 컬렉션 하나만 페치하므로 MultipleBagFetch 대상이 아니다.
     */
    @Query(
        "select distinct p from Problem p join fetch p.concepts " +
            "where p.integration = true and p.approvalStatus = watson.bytecs.problem.domain.ApprovalStatus.APPROVED",
    )
    fun findApprovedIntegrationProblemsWithConcepts(): List<Problem>

    /** JPQL 별칭 프로젝션 — [findConceptIdsOfIntegrationProblems]·[findConceptIdsByProblemIdIn] 전용(문제 id·개념 id 쌍). */
    interface ProblemConceptView {
        val problemId: Long
        val conceptId: Long
    }

    /**
     * 기동 시 콘텐츠 업서트([watson.bytecs.problem.infrastructure.ProblemDataLoader]) 전용 — 기존 전체 문제를
     * 심화 정보까지 함께(join fetch) 한 번에 조회한다. 문제별 개별 조회(N+1)를 피하고 메모리에서 질문 텍스트로
     * 매칭하기 위함이다(107건 수준이라 전량 로드가 안전하다). 승인 상태와 무관하게 전체를 대상으로 한다 —
     * 재저작 콘텐츠 갱신은 서빙 게이트와 별개의 관심사다.
     */
    @Query("select p from Problem p left join fetch p.enrichment")
    fun findAllWithEnrichment(): List<Problem>
}
