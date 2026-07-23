package watson.bytecs.interview.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 결정적 Fake 채점기: 정규화한 설명이 정규화한 루브릭 포인트를 포함하면 그 포인트는 충족이다. */
class FakeExplanationJudgeTest {

    private val judge = FakeExplanationJudge()

    @Test
    fun `설명이 포인트 문구를 포함하면 충족으로 판정한다`() {
        val result = judge.judge(
            rubricPoints = listOf("힙은 완전 이진 트리다", "삽입은 O(log n)이다"),
            explanation = "힙은 완전 이진 트리다. 그리고 다른 이야기.",
        )

        assertThat(result.satisfiedPoints).containsExactly(true, false)
    }

    @Test
    fun `공백과 대소문자 차이는 무시하고 비교한다`() {
        val result = judge.judge(
            rubricPoints = listOf("Big O Notation"),
            explanation = "이건  bigonotation  이야기예요",
        )

        assertThat(result.satisfiedPoints).containsExactly(true)
    }

    @Test
    fun `아무 포인트도 못 짚으면 전부 미충족이다`() {
        val result = judge.judge(
            rubricPoints = listOf("포인트A", "포인트B"),
            explanation = "전혀 관계없는 설명",
        )

        assertThat(result.satisfiedPoints).containsExactly(false, false)
    }
}
