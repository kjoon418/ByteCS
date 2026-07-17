package watson.bytecs.extrastudy.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.account.security.JwtTokenProvider
import watson.bytecs.extrastudy.infrastructure.ExtraStudyRepository
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Enrichment
import watson.bytecs.problem.domain.EnrichmentItem
import watson.bytecs.problem.domain.Hint
import watson.bytecs.problem.domain.MisconceptionHint
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * 추가 학습 API의 통합 계약을 검증한다(SessionControllerIntegrationTest 관례 차용).
 * 시드 고정 Random(마지막 후보 선택)과 고정 시계로 선정을 결정적으로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ExtraStudyControllerIntegrationTest.FixedClockConfig::class)
class ExtraStudyControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val extraStudyRepository: ExtraStudyRepository,
    @Autowired private val conceptMasteryRepository: ConceptMasteryRepository,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    private lateinit var token: String
    private var userId: Long = 0
    private var p1: Long = 0
    private var p2: Long = 0
    private var p3: Long = 0
    private val answersById = mutableMapOf<Long, String>()
    private val conceptNameById = mutableMapOf<Long, String>()

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val DAY1: LocalDate = LocalDate.of(2026, 7, 14)

        const val WEAK_HINT = "약한 힌트예요"
        const val STRONG_HINT = "강한 힌트예요"
        const val MISCONCEPTION_ANSWER = "흔한오답"
        const val MISCONCEPTION_MESSAGE = "그건 다른 개념이에요. 다시 도전해보세요!"
        const val ENRICHMENT_TITLE = "심화 제목이에요"
        const val ENRICHMENT_BODY = "심화 본문이에요"
        const val ENRICHMENT_ITEM_TITLE = "항목 제목이에요"
        const val ENRICHMENT_ITEM_DESC = "항목 설명이에요"
        const val ENRICHMENT_QUOTE = "인용 한 줄이에요"
    }

    @BeforeEach
    fun setUp() {
        conceptMasteryRepository.deleteAll()
        sessionRepository.deleteAll()
        extraStudyRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()
        answersById.clear()
        conceptNameById.clear()

        clock.setDate(DAY1)

        p1 = seedProblem("개념1", "정답1", "해설1")
        p2 = seedProblem("개념2", "정답2", "해설2")
        p3 = seedProblem("개념3", "정답3", "해설3")

        val user = userRepository.save(User.createGuest())
        userId = user.id
        token = jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    @Test
    fun `인증 없이 현재 문제를 조회하면 401을 반환한다`() {
        mockMvc.get("/api/extra-study/current")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `현재 문제를 조회하면 무낙인으로 한 문제를 열고 정답 단서를 노출하지 않는다`() {
        getCurrent(token).andExpect {
            status { isOk() }
            jsonPath("$.exhausted") { value(false) }
            jsonPath("$.problem.id") { exists() }
            jsonPath("$.problem.question") { exists() }
            jsonPath("$.problem.hintCount") { value(0) }
            jsonPath("$.problem.revealedHints") { isEmpty() }
            // no-leak: 정답을 유추할 수 있는 정보는 새어 나가지 않는다.
            jsonPath("$.problem.concepts") { doesNotExist() }
            jsonPath("$.problem.acceptableAnswers") { doesNotExist() }
            jsonPath("$.problem.representativeAnswer") { doesNotExist() }
            jsonPath("$.problem.explanation") { doesNotExist() }
            jsonPath("$.problem.enrichment") { doesNotExist() }
        }
    }

    @Test
    fun `열린 문제는 새로고침으로 다시 뽑히지 않고 그대로 이어진다`() {
        val first = currentProblemId(token)
        val second = currentProblemId(token)

        assertThat(first).isEqualTo(second)
        assertThat(extraStudyRepository.count()).isEqualTo(1)
    }

    @Test
    fun `정답을 제출하면 개념과 해설과 대표 정답을 공개한다`() {
        val id = currentProblemId(token)

        submitAttempt(token, answersById.getValue(id)).andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.concepts[0]") { exists() }
            jsonPath("$.explanation") { exists() }
            jsonPath("$.representativeAnswer") { value(answersById.getValue(id)) }
        }
    }

    @Test
    fun `오답을 제출하면 200이며 개념과 정답을 노출하지 않는다`() {
        currentProblemId(token)

        submitAttempt(token, "완전히 다른 답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("MISMATCH") }
            jsonPath("$.concepts") { value(nullValue()) }
            jsonPath("$.explanation") { value(nullValue()) }
            jsonPath("$.representativeAnswer") { value(nullValue()) }
        }
    }

    @Test
    fun `정답으로 통과하면 그 개념 숙련도가 생기고 다음 조회는 다른 문제를 연다`() {
        val id = currentProblemId(token)
        submitAttempt(token, answersById.getValue(id)).andExpect { status { isOk() } }

        // 무도움 통과 → 강한 정착(레벨 1, 복습 3일 뒤).
        val mastery = masteryOf(conceptNameOf(id))
        assertThat(mastery.level).isEqualTo(1)
        assertThat(mastery.nextReviewDate).isEqualTo(DAY1.plusDays(3))

        // 정답 후에는 열린 항목이 비워지므로 다음 조회가 새 문제를 연다.
        val next = currentProblemId(token)
        assertThat(next).isNotEqualTo(id)
    }

    @Test
    fun `추가 학습에서 푼 문제는 이후 세션 배정에서 제외된다`() {
        // 추가 학습에서 한 문제를 먼저 푼다.
        val solvedId = currentProblemId(token)
        submitAttempt(token, answersById.getValue(solvedId)).andExpect { status { isOk() } }

        // 이어서 만든 오늘 세션은 그 문제를 새 개념으로 다시 내지 않는다(세션 ∪ 추가 학습 합집합).
        val tree = objectMapper.readTree(getToday(token).andReturn().response.contentAsByteArray)
        assertThat(tree.get("totalCount").asInt()).isEqualTo(2)
        val assignedIds = tree.get("currentProblem").get("id").asLong()
        assertThat(assignedIds).isNotEqualTo(solvedId)
    }

    @Test
    fun `세션에서 푼 문제는 이후 추가 학습 선정에서 제외되어 소진으로 이어진다`() {
        // 세션에서 첫 문제(p1)를 푼다.
        getToday(token)
        submitSession(token, "정답1").andExpect { status { isOk() } }

        // 추가 학습에서 남은 문제를 모두 푼다. p1이 추가 학습 unseen에 남아 있으면 소진되지 않는다.
        solveEverythingInExtraStudy(token)

        getCurrent(token).andExpect {
            status { isOk() }
            jsonPath("$.exhausted") { value(true) }
            jsonPath("$.problem") { value(nullValue()) }
        }
    }

    @Test
    fun `복습 도래 문제를 안 푼 문제보다 먼저 연다`() {
        // p2 개념을 5일 전에 무도움 통과한 것으로 두면 복습(3일 뒤 = 2일 전)이 오늘 도래한다.
        conceptMasteryRepository.save(
            ConceptMastery.firstSolve(userId, conceptIdOf("개념2"), MasterySignal.UNAIDED, DAY1.minusDays(5), p2),
        )

        // 안 푼 문제 무작위(마지막 후보 p3)보다 도래 복습 p2가 우선한다.
        assertThat(currentProblemId(token)).isEqualTo(p2)
    }

    @Test
    fun `모든 문제를 풀면 소진 상태를 노출한다`() {
        solveEverythingInExtraStudy(token)

        getCurrent(token).andExpect {
            status { isOk() }
            jsonPath("$.exhausted") { value(true) }
            jsonPath("$.problem") { value(nullValue()) }
        }
    }

    @Test
    fun `열린 문제가 없을 때 답 제출은 409를 반환한다`() {
        submitAttempt(token, "아무 답").andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("EXTRA_STUDY_NO_OPEN_ITEM") }
        }
    }

    @Test
    fun `열린 문제가 없을 때 정답 공개는 409를 반환한다`() {
        reveal(token).andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("EXTRA_STUDY_NO_OPEN_ITEM") }
        }
    }

    @Test
    fun `시도 전에도 정답 공개가 허용되고 공개 후 따라 입력으로 통과한다`() {
        reseedSingleHintedProblem()
        getCurrent(token)

        reveal(token).andExpect {
            status { isOk() }
            jsonPath("$.concepts[0]") { value("힌트개념") }
            jsonPath("$.explanation") { value("해설") }
            jsonPath("$.representativeAnswer") { value("정답") }
        }

        submitAttempt(token, "정답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
        }

        // 정답 공개(포기) 후 통과 → 레벨 하락(0 바닥), 복습 1일 뒤.
        val mastery = masteryOf("힌트개념")
        assertThat(mastery.level).isEqualTo(0)
        assertThat(mastery.nextReviewDate).isEqualTo(DAY1.plusDays(1))
    }

    @Test
    fun `예상 오답을 제출하면 교정 힌트가 뜨고 오답으로 확정되지 않으며 이후 통과는 도움 정착이 된다`() {
        reseedSingleHintedProblem()
        getCurrent(token)

        submitAttempt(token, MISCONCEPTION_ANSWER).andExpect {
            status { isOk() }
            jsonPath("$.result") { value("MISMATCH") }
            jsonPath("$.misconceptionHint") { value(MISCONCEPTION_MESSAGE) }
            jsonPath("$.concepts") { value(nullValue()) }
        }

        submitAttempt(token, "정답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
        }

        // 교정 힌트를 본 뒤 통과 → 도움 정착(레벨 유지 0, 복습 1일 뒤).
        val mastery = masteryOf("힌트개념")
        assertThat(mastery.level).isEqualTo(0)
        assertThat(mastery.nextReviewDate).isEqualTo(DAY1.plusDays(1))
    }

    @Test
    fun `힌트 개수는 알리되 미공개 힌트 본문은 현재 문제 응답에 새지 않는다`() {
        reseedSingleHintedProblem()

        val result = getCurrent(token).andExpect {
            status { isOk() }
            jsonPath("$.problem.hintCount") { value(2) }
            jsonPath("$.problem.revealedHints") { isEmpty() }
        }.andReturn()

        assertThat(bodyOf(result)).doesNotContain(WEAK_HINT).doesNotContain(STRONG_HINT)
    }

    @Test
    fun `힌트를 열면 약한 것부터 공개되고 강한 힌트는 아직 새지 않는다`() {
        reseedSingleHintedProblem()
        getCurrent(token)

        val result = revealHint(token, 0).andExpect {
            status { isOk() }
            jsonPath("$.hintCount") { value(2) }
            jsonPath("$.revealedHints.length()") { value(1) }
            jsonPath("$.revealedHints[0].text") { value(WEAK_HINT) }
        }.andReturn()

        assertThat(bodyOf(result)).doesNotContain(STRONG_HINT)
    }

    @Test
    fun `공개 수가 어긋난 요청은 더블탭에도 힌트를 더 열지 않는다`() {
        reseedSingleHintedProblem()
        getCurrent(token)
        revealHint(token, 0)

        revealHint(token, 0).andExpect {
            status { isOk() }
            jsonPath("$.revealedHints.length()") { value(1) }
        }
    }

    @Test
    fun `심화 정보가 있는 문제를 정답 처리하면 함께 공개된다`() {
        reseedEnrichedProblem()
        getCurrent(token)

        submitAttempt(token, "정답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.enrichment.title") { value(ENRICHMENT_TITLE) }
            jsonPath("$.enrichment.items[0].title") { value(ENRICHMENT_ITEM_TITLE) }
            jsonPath("$.enrichment.quote") { value(ENRICHMENT_QUOTE) }
        }
    }

    @Test
    fun `계정을 삭제하면 그 사용자의 추가 학습도 함께 삭제된다`() {
        val id = currentProblemId(token)
        submitAttempt(token, answersById.getValue(id))
        currentProblemId(token) // 열린 항목 + solved 컬렉션을 채운다.
        assertThat(extraStudyRepository.count()).isEqualTo(1)
        assertThat(extraStudySolvedRowCount()).isEqualTo(1)

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        assertThat(extraStudyRepository.count()).isEqualTo(0)
        // 파생 delete가 아니면 @ElementCollection 테이블에 고아 행이 남는다. 이 단언이 그 가드다.
        assertThat(extraStudySolvedRowCount()).isEqualTo(0)
    }

    // --- helpers ---

    private fun solveEverythingInExtraStudy(bearer: String) {
        while (true) {
            val tree = objectMapper.readTree(getCurrent(bearer).andReturn().response.contentAsByteArray)
            if (tree.get("exhausted").asBoolean()) return
            val id = tree.get("problem").get("id").asLong()
            submitAttempt(bearer, answersById.getValue(id)).andExpect { status { isOk() } }
        }
    }

    private fun currentProblemId(bearer: String): Long {
        val tree = objectMapper.readTree(getCurrent(bearer).andReturn().response.contentAsByteArray)
        return tree.get("problem").get("id").asLong()
    }

    private fun getCurrent(bearer: String): ResultActionsDsl =
        mockMvc.get("/api/extra-study/current") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun submitAttempt(bearer: String, answer: String): ResultActionsDsl =
        mockMvc.post("/api/extra-study/attempts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"$answer"}"""
        }

    private fun reveal(bearer: String): ResultActionsDsl =
        mockMvc.post("/api/extra-study/reveal") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun revealHint(bearer: String, revealedCount: Int): ResultActionsDsl =
        mockMvc.post("/api/extra-study/hints/reveal") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"revealedCount":$revealedCount}"""
        }

    private fun getToday(bearer: String): ResultActionsDsl =
        mockMvc.get("/api/sessions/today") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun submitSession(bearer: String, answer: String): ResultActionsDsl =
        mockMvc.post("/api/sessions/today/attempts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"$answer"}"""
        }

    private fun bodyOf(result: MvcResult): String =
        result.response.contentAsByteArray.toString(Charsets.UTF_8)

    private fun extraStudySolvedRowCount(): Long =
        jdbcTemplate.queryForObject("select count(*) from extra_study_solved", Long::class.java)!!

    private fun conceptIdOf(conceptName: String): Long =
        conceptRepository.findAll().first { it.name == conceptName }.id

    private fun conceptNameOf(problemId: Long): String =
        conceptNameById.getValue(problemId)

    private fun masteryOf(conceptName: String): ConceptMastery =
        conceptMasteryRepository.findByUserIdAndConceptId(userId, conceptIdOf(conceptName))
            ?: error("개념 '$conceptName'의 숙련도가 없습니다.")

    private fun seedProblem(conceptName: String, answer: String, explanation: String): Long {
        val concept = conceptRepository.save(Concept(conceptName))
        val problem = problemRepository.save(
            Problem(
                questionText = "$conceptName 질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf(answer),
                representativeAnswer = answer,
                difficulty = Difficulty.EASY,
                explanation = explanation,
            ),
        )
        answersById[problem.id] = answer
        conceptNameById[problem.id] = conceptName
        return problem.id
    }

    /** 기존 시드(p1~p3)를 지우고, 힌트 2개(약→강)와 오답 교정 힌트 1개를 가진 문제 하나만 남긴다. */
    private fun reseedSingleHintedProblem() {
        extraStudyRepository.deleteAll()
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        answersById.clear()

        val concept = conceptRepository.save(Concept("힌트개념"))
        val problem = problemRepository.save(
            Problem(
                questionText = "힌트 문제",
                concepts = listOf(concept),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "해설",
                hints = listOf(Hint(WEAK_HINT), Hint(STRONG_HINT)),
                misconceptionHints = listOf(
                    MisconceptionHint(setOf(MISCONCEPTION_ANSWER), MISCONCEPTION_MESSAGE),
                ),
            ),
        )
        answersById[problem.id] = "정답"
    }

    /** 기존 시드(p1~p3)를 지우고, 심화 정보를 가진 문제 하나만 남긴다. */
    private fun reseedEnrichedProblem() {
        extraStudyRepository.deleteAll()
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        answersById.clear()

        val concept = conceptRepository.save(Concept("심화개념"))
        val problem = problemRepository.save(
            Problem(
                questionText = "심화 정보 문제",
                concepts = listOf(concept),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "해설",
                enrichment = Enrichment(
                    title = ENRICHMENT_TITLE,
                    body = ENRICHMENT_BODY,
                    items = listOf(EnrichmentItem(ENRICHMENT_ITEM_TITLE, ENRICHMENT_ITEM_DESC)),
                    quote = ENRICHMENT_QUOTE,
                ),
            ),
        )
        answersById[problem.id] = "정답"
    }

    /** 날짜를 테스트에서 결정적으로 고정·전진시키는 시계(세션 통합 테스트와 동형). */
    class MutableClock(private var current: Instant, private val zone: ZoneId) : Clock() {
        fun setDate(date: LocalDate) {
            current = date.atStartOfDay(zone).toInstant()
        }

        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
        override fun instant(): Instant = current
    }

    @TestConfiguration
    class FixedClockConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock =
            MutableClock(DAY1.atStartOfDay(KST).toInstant(), KST)

        /**
         * 선정을 결정적으로 만든다. nextInt(until)이 항상 until-1을 돌려주면
         *  - 세션 배정 셔플(SessionCreator)은 항등이 되어 id 오름차순 배정이 되고,
         *  - 추가 학습의 후보.random(random)은 마지막(가장 큰 id) 후보를 고른다.
         */
        @Bean
        @Primary
        fun deterministicRandom(): Random = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = until - 1
        }
    }
}
