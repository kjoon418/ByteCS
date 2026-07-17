package watson.bytecs.extrastudy.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.dao.DataIntegrityViolationException
import watson.bytecs.extrastudy.application.dto.ExtraStudyCurrentResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyProblemResponse
import watson.bytecs.extrastudy.domain.ExtraStudy
import watson.bytecs.extrastudy.infrastructure.ExtraStudyRepository
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.study.LearningHistory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

/**
 * get-or-create 경합 복구를 결정적으로 검증한다(REQUIRES_NEW 격리 덕분에 진 요청도 500 없이 기존 행을 얻는다).
 * 스레드 경합 대신, 협력자 스텁으로 '생성 실패 → 재조회 성공' 경로를 그대로 재현한다(SessionServiceTest 관례).
 */
class ExtraStudyServiceTest {

    private val extraStudyRepository: ExtraStudyRepository = mock(ExtraStudyRepository::class.java)
    private val problemRepository: ProblemRepository = mock(ProblemRepository::class.java)
    private val reviewService: ReviewService = mock(ReviewService::class.java)
    private val learningHistory: LearningHistory = mock(LearningHistory::class.java)
    private val responseMapper: ExtraStudyResponseMapper = mock(ExtraStudyResponseMapper::class.java)
    private val extraStudyCreator: ExtraStudyCreator = mock(ExtraStudyCreator::class.java)

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val clock: Clock = Clock.fixed(LocalDate.of(2026, 7, 17).atStartOfDay(zone).toInstant(), zone)

    private val service = ExtraStudyService(
        extraStudyRepository,
        problemRepository,
        reviewService,
        learningHistory,
        responseMapper,
        extraStudyCreator,
        clock,
    )

    @Test
    fun `생성 경합에서 진 요청은 500 없이 이미 만들어진 추가 학습을 돌려준다`() {
        val winner = ExtraStudy.create(userId = 1L).apply { assignOpen(10L) }
        val problem = Problem(
            questionText = "질문",
            concepts = listOf(Concept("개념")),
            acceptableAnswers = setOf("정답"),
            representativeAnswer = "정답",
        )
        val expected = ExtraStudyCurrentResponse(
            exhausted = false,
            problem = ExtraStudyProblemResponse(
                id = 10L,
                question = "질문",
                difficulty = null,
                codeSnippet = null,
                hintCount = 0,
                revealedHints = emptyList(),
                category = null,
            ),
        )

        // 최초 조회는 없음 → 생성 시도가 유니크 경합으로 실패 → 재조회에서 승자의 행(열린 문제 포함)을 본다.
        given(extraStudyRepository.findByUserId(1L)).willReturn(null, winner)
        given(extraStudyCreator.createInNewTransaction(1L))
            .willThrow(DataIntegrityViolationException("uk_extra_study_user"))
        given(problemRepository.findById(10L)).willReturn(Optional.of(problem))
        given(responseMapper.toCurrentResponse(problem, 0)).willReturn(expected)

        val result = service.getCurrent(1L)

        assertThat(result).isEqualTo(expected)
        // 사전 조회(null) 1회 + 경합 복구 재조회 1회.
        verify(extraStudyRepository, times(2)).findByUserId(1L)
    }

    @Test
    fun `재조회가 비는 무결성 위반은 그대로 전파되어 중립 CONFLICT로 매핑된다`() {
        // 생성이 무결성 위반으로 실패했는데 재조회도 비어 있으면(우리가 아는 경합이 아니면) 그대로 전파한다.
        // 전역 핸들러가 이를 중립 CONFLICT(409)로 매핑한다(SessionServiceTest와 같은 규약).
        given(extraStudyRepository.findByUserId(1L)).willReturn(null, null)
        given(extraStudyCreator.createInNewTransaction(1L))
            .willThrow(DataIntegrityViolationException("some other constraint"))

        assertThatThrownBy { service.getCurrent(1L) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
