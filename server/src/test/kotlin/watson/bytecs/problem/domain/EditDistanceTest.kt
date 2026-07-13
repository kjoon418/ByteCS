package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EditDistanceTest {

    @Test
    fun 완전히_같으면_0이다() {
        assertThat(EditDistance.levenshtein("collision", "collision")).isEqualTo(0)
    }

    @Test
    fun 한_글자_삭제는_거리가_1이다() {
        // "collsion"은 "collision"에서 i 하나가 빠진 형태다.
        assertThat(EditDistance.levenshtein("collsion", "collision")).isEqualTo(1)
    }

    @Test
    fun 한_글자_교체는_거리가_1이다() {
        assertThat(EditDistance.levenshtein("cache", "cacse")).isEqualTo(1)
    }

    @Test
    fun 두_글자_교체는_거리가_2이다() {
        assertThat(EditDistance.levenshtein("cache", "caxxe")).isEqualTo(2)
    }

    @Test
    fun 한쪽이_비어_있으면_다른_쪽_길이다() {
        assertThat(EditDistance.levenshtein("", "stack")).isEqualTo(5)
        assertThat(EditDistance.levenshtein("stack", "")).isEqualTo(5)
    }
}
