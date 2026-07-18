package watson.bytecs.problem.infrastructure

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemCategory

/**
 * [ProblemRepository]가 실제 DB 영속화·재로딩을 거쳐도 개념 순서·대표 분류 도출을 지키는지 검증한다.
 * [watson.bytecs.problem.domain.ProblemRepresentativeCategoryTest]는 영속화 없이 객체 생성만으로
 * [Problem.representativeCategory]를 검증하므로, `@OrderColumn`이 DB 왕복(저장 → 재조회)에서도
 * 순서를 보존하는지는 이 통합 테스트가 메꾼다.
 */
@SpringBootTest
class ProblemRepositoryIntegrationTest(
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val entityManager: EntityManager,
) {

    @BeforeEach
    fun setUp() {
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
    }

    @Test
    @Transactional
    fun `여러 개념을 태깅한 문제를 저장 후 재조회해도 대표 분류는 첫 번째 개념의 카테고리다`() {
        // given
        val representative = conceptRepository.save(Concept("스택", category = ProblemCategory.DATA_STRUCTURE))
        val other = conceptRepository.save(Concept("힙 정렬", category = ProblemCategory.ALGORITHM))
        val saved = problemRepository.save(
            Problem(
                questionText = "질문",
                concepts = listOf(representative, other),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
            ),
        )
        // 저장 직후 재조회는 1차 캐시로 같은 인스턴스를 돌려줄 수 있어, DB 왕복을 강제하려면
        // 영속성 컨텍스트를 비워야 한다(같은 트랜잭션 안에서 flush로 DB에 반영한 뒤 clear).
        entityManager.flush()
        entityManager.clear()

        // when
        val reloaded = problemRepository.findById(saved.id).orElseThrow()

        // then
        assertThat(reloaded.conceptNames()).containsExactly("스택", "힙 정렬")
        assertThat(reloaded.representativeCategory()).isEqualTo(ProblemCategory.DATA_STRUCTURE)
    }

    @Test
    fun `findAllByIdWithConceptsAndEnrichment로 즉시 로딩해도 개념 순서와 대표 분류가 유지된다`() {
        // 카테고리별 학습 이력 조회(N+1 회피 fetch join)가 순서를 깨지 않는지 검증한다.
        // given
        val representative = conceptRepository.save(Concept("TCP 3-way handshake", category = ProblemCategory.NETWORK))
        val other = conceptRepository.save(Concept("정규화 제3정규형", category = ProblemCategory.DATABASE))
        val saved = problemRepository.save(
            Problem(
                questionText = "질문",
                concepts = listOf(representative, other),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
            ),
        )

        // when
        val reloaded = problemRepository.findAllByIdWithConceptsAndEnrichment(listOf(saved.id)).single()

        // then
        assertThat(reloaded.conceptNames()).containsExactly("TCP 3-way handshake", "정규화 제3정규형")
        assertThat(reloaded.representativeCategory()).isEqualTo(ProblemCategory.NETWORK)
    }

    @Test
    fun `배정 후보 조회는 승인 상태의 문제만 돌려준다`() {
        // given — 다섯 승인 상태를 전부 저장한다(서빙 게이트의 공통 근원 쿼리 검증).
        val concept = conceptRepository.save(Concept("스택"))
        val approved = saveProblemWithStatus(concept, ApprovalStatus.APPROVED)
        saveProblemWithStatus(concept, ApprovalStatus.DRAFT)
        saveProblemWithStatus(concept, ApprovalStatus.IN_REVIEW)
        saveProblemWithStatus(concept, ApprovalStatus.REJECTED)
        saveProblemWithStatus(concept, ApprovalStatus.RETRACTED)

        // when
        val candidateIds = problemRepository.findApprovedIdsOrderByIdAsc()

        // then
        assertThat(candidateIds).containsExactly(approved.id)
    }

    @Test
    fun `개념별 후보 조회는 그 개념의 승인 문제만 돌려준다`() {
        // given — 유도형 복습 예외가 새 문제를 꺼내오는 쿼리라, 비승인 문제가 섞이면 초안이 사용자에게 노출된다.
        val targetConcept = conceptRepository.save(Concept("해시 충돌"))
        val otherConcept = conceptRepository.save(Concept("큐"))
        val approved = saveProblemWithStatus(targetConcept, ApprovalStatus.APPROVED)
        saveProblemWithStatus(targetConcept, ApprovalStatus.DRAFT)
        saveProblemWithStatus(targetConcept, ApprovalStatus.RETRACTED)
        saveProblemWithStatus(otherConcept, ApprovalStatus.APPROVED)

        // when
        val candidateIds = problemRepository.findApprovedIdsByConceptIdOrderByIdAsc(targetConcept.id)

        // then
        assertThat(candidateIds).containsExactly(approved.id)
    }

    private fun saveProblemWithStatus(concept: Concept, status: ApprovalStatus): Problem =
        problemRepository.save(
            Problem(
                approvalStatus = status,
                questionText = "질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
            ),
        )
}
