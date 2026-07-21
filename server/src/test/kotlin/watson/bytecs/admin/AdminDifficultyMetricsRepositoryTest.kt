package watson.bytecs.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.LocalDate

/**
 * 난이도 조절 1차 관리자 지표(선호 난이도 분포·난이도별 정답 공개율)의 집계 쿼리를 실제 DB 왕복으로 검증한다.
 * 화면 조립(0 채움·라벨·비율)은 [AdminStatsService]가 담당하므로, 여기서는 그룹 쿼리의 집계값 자체만 못박는다.
 */
@SpringBootTest
class AdminDifficultyMetricsRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val conceptRepository: ConceptRepository,
) {

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `선호 난이도 분포는 학습자를 난이도별로 집계하고 관리자는 제외한다`() {
        // 학습자: 쉬움 2, 어려움 1, 미설정 3(순수 게스트). 관리자 1은 학습자가 아니므로 미설정에 포함되지 않아야 한다.
        userRepository.save(guestWithPreferred(Difficulty.EASY))
        userRepository.save(guestWithPreferred(Difficulty.EASY))
        userRepository.save(guestWithPreferred(Difficulty.HARD))
        userRepository.save(User.createGuest())
        userRepository.save(User.createGuest())
        userRepository.save(User.createGuest())
        userRepository.save(User.createAdmin(Email("admin@bytecs.dev"), "hash"))

        val countByDifficulty = userRepository.countLearnersByPreferredDifficulty()
            .associate { it.difficulty to it.count }

        assertThat(countByDifficulty[Difficulty.EASY]).isEqualTo(2)
        assertThat(countByDifficulty[Difficulty.HARD]).isEqualTo(1)
        assertThat(countByDifficulty[Difficulty.MEDIUM]).isNull() // 아무도 고르지 않은 난이도는 그룹에 없다(서비스가 0으로 채움).
        assertThat(countByDifficulty[null]).isEqualTo(3) // 관리자 제외 — 순수 게스트 3명만.
    }

    @Test
    fun `난이도별 정답 공개율 원자료는 푼 문제만 세고 그중 정답을 공개한 칸을 집계한다`() {
        // 쉬움 2문제·어려움 1문제를 한 세션에서 모두 풀되, 쉬움 하나와 어려움 하나에서 정답을 공개한다.
        val concept = conceptRepository.save(Concept("스택"))
        val easy1 = saveProblem(concept, Difficulty.EASY)
        val easy2 = saveProblem(concept, Difficulty.EASY)
        val hard1 = saveProblem(concept, Difficulty.HARD)

        val session = Session.assign(1L, TODAY, listOf(easy1.id, easy2.id, hard1.id))
        session.reveal() // easy1(pos0) 정답 공개
        session.recordAttempt(Judgement.CORRECT, AnswerText("정답")) // easy1 풀이 → pos1
        session.recordAttempt(Judgement.CORRECT, AnswerText("정답")) // easy2 풀이(공개 없음) → pos2
        session.reveal() // hard1(pos2) 정답 공개
        session.recordAttempt(Judgement.CORRECT, AnswerText("정답")) // hard1 풀이 → 완료
        sessionRepository.save(session)

        val rowByDifficulty = sessionRepository.countSolvedItemRevealsByDifficulty()
            .associateBy { it.difficulty }

        // 쉬움: 푼 2 중 공개 1. 어려움: 푼 1 중 공개 1. 보통: 푼 문제 없음 → 그룹에 없다.
        assertThat(rowByDifficulty[Difficulty.EASY]?.solvedCount).isEqualTo(2)
        assertThat(rowByDifficulty[Difficulty.EASY]?.revealedCount).isEqualTo(1)
        assertThat(rowByDifficulty[Difficulty.HARD]?.solvedCount).isEqualTo(1)
        assertThat(rowByDifficulty[Difficulty.HARD]?.revealedCount).isEqualTo(1)
        assertThat(rowByDifficulty[Difficulty.MEDIUM]).isNull()
    }

    @Test
    fun `풀지 않은(미도달) 칸은 정답 공개율 분모에 들어가지 않는다`() {
        // 두 문제 중 첫 문제만 풀고 세션을 미완료로 둔다 — 두 번째(미도달) 칸은 solved=false라 집계 대상이 아니다.
        val concept = conceptRepository.save(Concept("큐"))
        val solvedEasy = saveProblem(concept, Difficulty.EASY)
        val unreachedEasy = saveProblem(concept, Difficulty.EASY)

        val session = Session.assign(2L, TODAY, listOf(solvedEasy.id, unreachedEasy.id))
        session.recordAttempt(Judgement.CORRECT, AnswerText("정답")) // 첫 문제만 풀이(미완료)
        sessionRepository.save(session)

        val easyRow = sessionRepository.countSolvedItemRevealsByDifficulty()
            .single { it.difficulty == Difficulty.EASY }

        assertThat(easyRow.solvedCount).isEqualTo(1) // 미도달 칸은 분모에서 빠진다.
        assertThat(easyRow.revealedCount).isEqualTo(0)
    }

    private fun guestWithPreferred(difficulty: Difficulty): User =
        User.createGuest().apply { updatePreferredDifficulty(difficulty) }

    private fun saveProblem(concept: Concept, difficulty: Difficulty): Problem =
        problemRepository.save(
            Problem(
                questionText = "질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
                difficulty = difficulty,
            ),
        )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 7, 21)
    }
}
