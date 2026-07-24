package watson.bytecs.interview.infrastructure

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
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.infrastructure.ConceptRepository

/**
 * [InterviewPromptDataLoader]의 기동 시 업서트 경로를 실제 DB(H2) 위에서 끝까지 실행한다
 * (코드 리뷰 M1 — Mockito 기반 [InterviewPromptDataLoaderTest]가 가릴 수 있는 실 DB 라운드트립 공백을 메운다).
 * [watson.bytecs.problem.infrastructure.ProblemDataLoaderPersistenceTest]와 같은 이유로 로더를 직접 생성해 호출하고
 * 클래스에 [Transactional]을 붙인다(`@Profile("local", "tester")` 빈이라 `test` 프로파일 컨텍스트에는 등록되지 않고,
 * `run()`의 `@Transactional`도 스프링 프록시를 거치지 않으면 적용되지 않는다 — 실제로 처음엔 이게 없어서
 * `findAllWithConcept()`가 돌려준 엔티티의 지연 로딩 `hints`가 두 번째 호출 시점엔 세션이 끊겨
 * `LazyInitializationException`이 났다. 그래서 로더가 변경분을 명시적으로 save한다).
 */
@SpringBootTest
@Transactional
class InterviewPromptDataLoaderPersistenceTest(
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val interviewPromptRepository: InterviewPromptRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
) {

    private fun loader(resourcePath: String) =
        InterviewPromptDataLoader(conceptRepository, interviewPromptRepository, objectMapper, resourcePath)

    @BeforeEach
    fun setUp() {
        interviewPromptRepository.deleteAll()
        conceptRepository.deleteAll()
        // 면접 질문 시드는 개념을 이름으로 참조한다(문제 시드가 먼저 만든다는 전제) — 여기선 문제 로더 없이 직접 심는다.
        conceptRepository.save(Concept("스택"))
    }

    @Test
    fun `기존 면접 질문에 재저작 시드를 업서트하면 힌트가 실제로 교체된다`() {
        // given: 최초 기동(DB가 비어있으니 전량 삽입 경로)으로 힌트를 심는다.
        loader("seed/interview-prompts-fixture.json").run()
        val original = interviewPromptRepository.findAllWithConcept().single()
        val originalId = original.id

        // 두 번째 기동이 1차 캐시가 아니라 실제 DB 행에서 다시 구성한 객체로 비교하도록 영속성 컨텍스트를 비운다.
        entityManager.flush()
        entityManager.clear()

        // when: 개념·질문은 같고 힌트만 바뀐 재저작 시드로 다시 기동한다(업서트 경로).
        loader("seed/interview-prompts-fixture-revised-hints.json").run()
        entityManager.flush()
        entityManager.clear()

        // then: 재조회(새 쿼리)해도 새 힌트가 반영된다 — 같은 행(id 불변)의 hints 컬렉션만 바뀐다.
        val reloaded = interviewPromptRepository.findAllWithConcept().single()
        assertThat(reloaded.id).isEqualTo(originalId)
        assertThat(reloaded.hints).containsExactly("재저작된 첫 번째 힌트입니다.", "재저작된 두 번째 힌트입니다.")
    }

    @Test
    fun `같은 시드로 재기동해도 추가 쓰기가 없다(멱등)`() {
        // given: 최초 기동으로 힌트를 심는다.
        loader("seed/interview-prompts-fixture.json").run()

        // 두 번째 기동이 1차 캐시가 아니라 실제 DB 행(@OrderColumn 순서 포함)에서 다시 구성한 컬렉션으로
        // 비교하도록 영속성 컨텍스트를 비운다.
        entityManager.flush()
        entityManager.clear()

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()

        // when: 완전히 같은 내용의 시드로 재기동한다 — 매칭은 되지만 힌트가 같으므로 갱신을 스킵해야 한다.
        loader("seed/interview-prompts-fixture.json").run()
        entityManager.flush()

        // then: 엔티티(면접 질문 자체) 삽입·수정·삭제가 없고, 소유 컬렉션(hints)에 대한 갱신 SQL도 발행되지 않는다.
        assertThat(statistics.entityInsertCount).isEqualTo(0)
        assertThat(statistics.entityUpdateCount).isEqualTo(0)
        assertThat(statistics.entityDeleteCount).isEqualTo(0)
        assertThat(statistics.collectionUpdateCount).isEqualTo(0)
    }
}
