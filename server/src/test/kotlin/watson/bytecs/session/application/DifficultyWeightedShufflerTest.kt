package watson.bytecs.session.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.bytecs.problem.domain.Difficulty
import kotlin.random.Random

/**
 * 난이도 가중 셔플러의 '결정 로직'을 검증한다(통계적 단정이 아니라 가중치 계산·후보 보존·결정성).
 * 실제 추첨 분포는 운영 튜닝 대상이라, 여기선 규칙(6:3:1)과 불변식(완전 배제 없음·결정성)만 못박는다.
 */
class DifficultyWeightedShufflerTest {

    private val shuffler = DifficultyWeightedShuffler()

    @Test
    fun `선호와의 거리로 가중치가 6대3대1로 정해진다`() {
        // 보통(MEDIUM) 선호: 보통=거리0=6, 쉬움·어려움=거리1=3.
        assertThat(shuffler.weightOf(Difficulty.MEDIUM, Difficulty.MEDIUM)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_NEAR)
        assertThat(shuffler.weightOf(Difficulty.EASY, Difficulty.MEDIUM)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_MID)
        assertThat(shuffler.weightOf(Difficulty.HARD, Difficulty.MEDIUM)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_MID)
    }

    @Test
    fun `선호가 끝단이면 반대 끝단은 거리2로 최소 가중치를 받는다`() {
        // 쉬움(EASY) 선호: 쉬움=6, 보통=3, 어려움=거리2=1.
        assertThat(shuffler.weightOf(Difficulty.EASY, Difficulty.EASY)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_NEAR)
        assertThat(shuffler.weightOf(Difficulty.MEDIUM, Difficulty.EASY)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_MID)
        assertThat(shuffler.weightOf(Difficulty.HARD, Difficulty.EASY)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_FAR)
    }

    @Test
    fun `난이도 미상은 가장 먼 거리로 보아 최소 가중치를 받는다`() {
        // null이어도 완전 배제되지 않도록 최소 가중치(1)를 준다.
        assertThat(shuffler.weightOf(null, Difficulty.EASY)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_FAR)
        assertThat(shuffler.weightOf(null, Difficulty.MEDIUM)).isEqualTo(DifficultyWeightedShuffler.WEIGHT_FAR)
    }

    @Test
    fun `모든 후보가 정확히 한 번씩 포함된다(완전 배제 없음)`() {
        val candidates = listOf(1L, 2L, 3L, 4L, 5L)
        val difficulties = mapOf(
            1L to Difficulty.EASY, 2L to Difficulty.HARD, 3L to Difficulty.HARD,
            4L to Difficulty.HARD, 5L to Difficulty.HARD,
        )

        // 선호 EASY라 어려움이 대부분이어도, 어려움 후보가 하나도 빠지지 않아야 한다.
        val ordered = shuffler.order(candidates, difficulties, Difficulty.EASY, Random(1L))

        assertThat(ordered).containsExactlyInAnyOrderElementsOf(candidates)
    }

    @Test
    fun `후보에 없는 난이도는 정렬 대상이 아니다(후보 있는 난이도에만 가중)`() {
        // 보통(MEDIUM) 난이도 후보가 아예 없다 — 셔플러는 존재하는 후보(쉬움·어려움)만 순서화한다.
        val candidates = listOf(10L, 20L)
        val difficulties = mapOf(10L to Difficulty.EASY, 20L to Difficulty.HARD)

        val ordered = shuffler.order(candidates, difficulties, Difficulty.MEDIUM, Random(7L))

        assertThat(ordered).containsExactlyInAnyOrder(10L, 20L)
    }

    @Test
    fun `같은 후보와 같은 시드면 항상 같은 순서가 나온다`() {
        val candidates = listOf(1L, 2L, 3L, 4L, 5L, 6L)
        val difficulties = candidates.associateWith { Difficulty.MEDIUM }

        val first = shuffler.order(candidates, difficulties, Difficulty.EASY, Random(42L))
        val second = shuffler.order(candidates, difficulties, Difficulty.EASY, Random(42L))

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `시드가 다르면 다른 순서가 나올 수 있다(실제로 무작위 추첨이 일어난다)`() {
        val candidates = listOf(1L, 2L, 3L, 4L, 5L, 6L)
        val difficulties = candidates.associateWith { Difficulty.MEDIUM }

        val orders = (1L..30L).map { seed ->
            shuffler.order(candidates, difficulties, Difficulty.MEDIUM, Random(seed))
        }

        assertThat(orders.distinct().size).isGreaterThan(1)
    }
}
