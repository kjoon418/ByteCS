package watson.bytecs.problem.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * [ProblemDataLoader]의 기동 시 업서트 경로를 실제 DB(H2) 위에서 끝까지 실행해, Mockito 기반
 * [ProblemDataLoaderTest]가 가릴 수 있는 두 가지를 검증한다(코드 리뷰 M1 — 실 DB 라운드트립 공백):
 *  1. cascade(ALL)+orphanRemoval로 심화 정보를 교체할 때 실제 DELETE+INSERT(+FK UPDATE)가 플러시되어
 *     내용이 재조회로도 바뀌는가.
 *  2. **멱등성** — 같은 시드로 재기동해도 심화 정보 행이 다시 교체되지 않는가. [ProblemDataLoader.enrichmentContentEquals]가
 *     DB 저장 → 재로딩을 거친 뒤에도 "같다"고 판정하는지(@OrderColumn 항목 순서·quote null 왕복 포함)를,
 *     하이버네이트 Statistics로 실제 INSERT/UPDATE/DELETE가 0건임을 확인해 단정한다
 *     (기존 관례 [watson.bytecs.session.application.SessionCreatorConnectionGateQueryCountTest] 참고).
 *
 * 로더는 `@Profile("local", "tester")` 빈이라 `test` 프로파일에서는 컨텍스트에 등록되지 않는다
 * ([InterviewSessionServicePersistenceTest]와 달리, 실 빈을 오토와이어해 호출할 수 없다 — 저장소 빈만 주입받고
 * 로더 자체는 생성자로 직접 만든다). 이렇게 `new`로 직접 만든 로더의 `run()`은 스프링 프록시를 거치지 않으므로
 * `@Transactional` 애노테이션이 실제 트랜잭션 경계로 작동하지 않는다 — 그래서 클래스 자체에 [Transactional]을 붙여
 * (a) `findAllWithEnrichment()`가 돌려주는 엔티티가 두 번째 `run()` 호출까지 매니지드 상태로 남아 있게 하고
 * (b) 이 테스트가 커밋한 행이 다른 영속성 테스트 클래스로 새지 않게 한다(같은 H2 컨텍스트를 공유하는 다른 클래스와의
 * FK 오염을 막는다 — 처음엔 이 클래스에 `@Transactional`이 없어 `InterviewPromptDataLoaderPersistenceTest`가 남긴
 * 커밋 행 때문에 `concept` 삭제가 FK 위반으로 실패했었다). 두 `run()` 호출 사이에는 [entityManager]로 flush+clear해
 * 두 번째 호출이 1차 캐시가 아니라 실제 DB 행에서 다시 구성한 객체 그래프로 비교하게 한다(진짜 라운드트립).
 * 로더 구현 자체는 변경분을 [ProblemRepository.save]로 명시적으로 저장한다(더티체킹 단독 의존 금지) — 스프링이
 * 관리하는 운영 기동 경로에서도 이 명시적 저장이 안전망이 된다.
 */
@SpringBootTest
@Transactional
class ProblemDataLoaderPersistenceTest(
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
) {

    private fun loader(resourcePath: String) =
        ProblemDataLoader(conceptRepository, problemRepository, objectMapper, resourcePath)

    @BeforeEach
    fun setUp() {
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
    }

    @Test
    fun `기존 문제에 재저작 시드를 업서트하면 심화 정보가 실제로 교체된다`() {
        // given: 최초 기동(DB가 비어있으니 전량 삽입 경로)으로 심화 정보를 심는다.
        loader("seed/upsert-single-problem-fixture.json").run()
        val original = problemRepository.findAllWithEnrichment().single()
        val originalProblemId = original.id
        val originalEnrichmentId = original.enrichment!!.id

        // 두 번째 기동이 1차 캐시가 아니라 실제 DB 행에서 다시 구성한 객체로 비교하도록 영속성 컨텍스트를 비운다.
        entityManager.flush()
        entityManager.clear()

        // when: 질문 텍스트는 같고 심화 정보만 바뀐 재저작 시드로 다시 기동한다(DB에 이미 데이터가 있으니 업서트 경로).
        loader("seed/upsert-single-problem-fixture-revised-enrichment.json").run()
        entityManager.flush()
        entityManager.clear()

        // then: 재조회(새 쿼리)해도 새 내용이 반영된다 — 문제 행 자체는 그대로, 심화 정보만 새 행으로 교체된다(orphanRemoval).
        val reloaded = problemRepository.findAllWithEnrichment().single()
        assertThat(reloaded.id).isEqualTo(originalProblemId)
        assertThat(reloaded.enrichment!!.id).isNotEqualTo(originalEnrichmentId)
        assertThat(reloaded.enrichment!!.title).isEqualTo("재저작된 제목")
        assertThat(reloaded.enrichment!!.body).isEqualTo("재저작된 본문 — 업서트가 실제로 심화 정보를 교체하는지 확인하는 픽스처다.")
        assertThat(reloaded.enrichment!!.items.map { it.title }).containsExactly("재저작 항목 01", "재저작 항목 02")
        assertThat(reloaded.enrichment!!.quote).isEqualTo("재저작된 인용.")
    }

    @Test
    fun `같은 시드로 재기동해도 추가 쓰기가 없다(멱등)`() {
        // given: 최초 기동으로 심화 정보를 심는다.
        loader("seed/upsert-single-problem-fixture.json").run()
        val original = problemRepository.findAllWithEnrichment().single()
        val originalEnrichmentId = original.enrichment!!.id

        // 두 번째 기동이 1차 캐시가 아니라 실제 DB 행(@OrderColumn 항목 순서·quote null 포함)에서 다시 구성한
        // 객체로 enrichmentContentEquals를 판정하도록 영속성 컨텍스트를 비운다.
        entityManager.flush()
        entityManager.clear()

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        // when: 완전히 같은 내용의 시드로 재기동한다 — 매칭은 되지만 콘텐츠가 같으므로 갱신을 스킵해야 한다.
        loader("seed/upsert-single-problem-fixture.json").run()
        entityManager.flush()

        // then: 심화 정보 행이 교체되지 않아 id가 그대로고, 엔티티 삽입·수정·삭제가 전혀 발행되지 않는다
        // (orphanRemoval에 의한 DELETE+INSERT도, 문제 행의 enrichment_id FK UPDATE도 없다).
        val reloaded = problemRepository.findAllWithEnrichment().single()
        assertThat(reloaded.enrichment!!.id).isEqualTo(originalEnrichmentId)
        assertThat(statistics.entityInsertCount).isEqualTo(0)
        assertThat(statistics.entityUpdateCount).isEqualTo(0)
        assertThat(statistics.entityDeleteCount).isEqualTo(0)
    }
}
