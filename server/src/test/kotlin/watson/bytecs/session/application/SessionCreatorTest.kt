package watson.bytecs.session.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserSettings
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.LocalDate
import java.util.Optional

/**
 * 세션 편입(§3 340행) 알고리즘을 검증한다.
 * 복습 문제 선정 자체는 [ReviewService]에 위임하므로 여기선 stub하고, 병합(복습 우선·중복 제거·분량 절단·폴백)만 검증한다.
 * 같은 학습 상태면 같은 세션이 나오는 결정성이 핵심이다.
 */
class SessionCreatorTest {

    private val sessionRepository = mock(SessionRepository::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val reviewService = mock(ReviewService::class.java)
    private val creator = SessionCreator(sessionRepository, problemRepository, userRepository, reviewService)

    private companion object {
        const val USER_ID = 1L
        val TODAY: LocalDate = LocalDate.of(2026, 7, 14)
    }

    @Test
    fun `복습이 없으면 아직 안 푼 문제를 id 오름차순으로 분량만큼 배정한다`() {
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L), solved = emptyList(), reviews = emptyList())

        assertThat(assignedIds).containsExactly(1L, 2L)
    }

    @Test
    fun `도래한 복습 문제를 먼저 배정하고 남은 칸을 새 개념으로 채운다`() {
        // 복습 30번 먼저, 남은 한 칸은 아직 안 푼 최소 id(1번).
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L, 30L), solved = listOf(30L), reviews = listOf(30L))

        assertThat(assignedIds).containsExactly(30L, 1L)
    }

    @Test
    fun `복습이 세션 분량을 넘으면 도래 순으로 자르고 새 개념은 들어가지 않는다`() {
        // 상한 없음: 도래 복습이 세션을 다 채울 수 있다. 초과분은 도래 순 우선으로 잘린다.
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L, 40L, 50L, 60L), solved = listOf(40L, 50L, 60L), reviews = listOf(40L, 50L, 60L))

        assertThat(assignedIds).containsExactly(40L, 50L)
    }

    @Test
    fun `복습과 새 개념이 겹치면 중복을 선착순으로 제거한다`() {
        // 복습 후보 2번이 아직 안 푼 목록에도 있으면(안 낸 유도형 예외 등), 복습으로 한 번만 편입된다.
        val assignedIds = assign(size = 3, all = listOf(1L, 2L, 3L), solved = emptyList(), reviews = listOf(2L))

        assertThat(assignedIds).containsExactly(2L, 1L, 3L)
    }

    @Test
    fun `새로 풀 문제가 없으면 전체 풀에서 폴백 배정한다`() {
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L), solved = listOf(1L, 2L, 3L), reviews = emptyList())

        assertThat(assignedIds).containsExactly(1L, 2L)
    }

    @Test
    fun `같은 학습 상태면 항상 같은 세션이 만들어진다`() {
        val first = assign(size = 2, all = listOf(1L, 2L, 3L, 30L), solved = listOf(30L), reviews = listOf(30L))
        val second = assign(size = 2, all = listOf(1L, 2L, 3L, 30L), solved = listOf(30L), reviews = listOf(30L))

        assertThat(first).isEqualTo(second)
    }

    /** 주어진 학습 상태를 stub하고 createInNewTransaction을 구동해, 배정된 본 문제 id를 순서대로 돌려준다. */
    private fun assign(size: Int, all: List<Long>, solved: List<Long>, reviews: List<Long>): List<Long> {
        val user = User.createGuest().apply { updateSettings(UserSettings(size)) }

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user))
        given(problemRepository.findAllIdsOrderByIdAsc()).willReturn(all)
        given(sessionRepository.findAssignedProblemIds(user.id)).willReturn(solved)
        given(sessionRepository.findSolvedProblemIds(user.id)).willReturn(solved)
        given(reviewService.selectDueReviewProblemIds(user.id, TODAY, solved.toSet(), all.toSet()))
            .willReturn(reviews)
        given(sessionRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Session::class.java)))
            .willAnswer { it.getArgument(0) }

        val session = creator.createInNewTransaction(USER_ID, TODAY)
        return session.items.map { it.problemId }
    }
}
