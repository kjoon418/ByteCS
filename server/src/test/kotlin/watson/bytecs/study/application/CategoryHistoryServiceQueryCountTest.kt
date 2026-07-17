package watson.bytecs.study.application

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemCategory
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.LocalDate

/**
 * 카테고리별 학습 이력 조회의 N+1 회귀를 하이버네이트 Statistics로 검증한다(코드 리뷰 F1).
 * 문제당 대표 분류(개념)·엔리치먼트 지연 로딩이 그대로면 쿼리 수가 푼 문제 수에 비례해 늘어난다 —
 * [ProblemRepository.findAllByIdWithConceptsAndEnrichment]가 이를 고정 쿼리 수로 접어야 한다.
 */
@SpringBootTest
class CategoryHistoryServiceQueryCountTest(
    @Autowired private val categoryHistoryService: CategoryHistoryService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
) {

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()

        userId = userRepository.save(User.createGuest()).id

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
    }

    @Test
    fun `카테고리별 이력 조회 쿼리 수는 푼 문제 수와 무관하게 고정된다`() {
        val queryCountForOneProblem = queryCountForSolvedProblems(problemCount = 1)
        val queryCountForFiveProblems = queryCountForSolvedProblems(problemCount = 5)

        // N+1이 남아 있었다면 문제 수만큼(대표 분류·엔리치먼트 지연 로딩) 쿼리가 늘어나 두 값이 달라진다.
        assertThat(queryCountForFiveProblems).isEqualTo(queryCountForOneProblem)
    }

    /** [problemCount]개의 멀티 개념 문제를 한 세션에서 모두 정답 처리한 뒤, 카테고리별 이력 조회 1회의 쿼리 수를 돌려준다. */
    private fun queryCountForSolvedProblems(problemCount: Int): Long {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()

        val problemIds = (1..problemCount).map { index -> seedMultiConceptProblem(index) }
        val session = Session.assign(userId, LocalDate.now(), problemIds)
        repeat(problemCount) { session.recordAttempt(Judgement.CORRECT, AnswerText("정답")) }
        sessionRepository.save(session)

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()

        categoryHistoryService.findByCategory(userId)

        return statistics.queryExecutionCount
    }

    /** 대표 개념·부개념을 각각 다른 카테고리로 둔 문제를 저장한다(concepts 컬렉션 지연 로딩이 실제로 발생할 조건). */
    private fun seedMultiConceptProblem(index: Int): Long {
        val categories = ProblemCategory.entries
        val representative = conceptRepository.save(
            Concept("대표개념$index", category = categories[index % categories.size]),
        )
        val other = conceptRepository.save(
            Concept("부개념$index", category = categories[(index + 1) % categories.size]),
        )
        return problemRepository.save(
            Problem(
                questionText = "질문$index",
                concepts = listOf(representative, other),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
            ),
        ).id
    }
}
