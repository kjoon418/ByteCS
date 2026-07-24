package watson.bytecs.interview.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import watson.bytecs.interview.domain.ExplanationJudge

/**
 * 채점기 공급자 스위치(C3)의 배선을 DB 없이 검증한다: provider 값에 따라 [ExplanationJudge] 빈이 **정확히 하나** 선택되고,
 * 두 후보(Fake/Http)의 @ConditionalOnProperty가 상호 배타라 충돌이 없음을 보장한다.
 * (로컬=무료 Fake / 운영=실 AI 전환이 이 한 프로퍼티로 안전하게 갈리는지가 오너 요구의 핵심 — 2026-07-24.)
 */
class InterviewJudgeConfigTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RestClientAutoConfiguration::class.java,
                JacksonAutoConfiguration::class.java,
            ),
        )
        .withUserConfiguration(InterviewJudgeConfig::class.java, FakeExplanationJudge::class.java)

    @Test
    fun `provider 미설정이면 Fake가 유일한 채점기다(로컬·테스트 무료·오프라인)`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(ExplanationJudge::class.java)
            assertThat(context.getBean(ExplanationJudge::class.java)).isInstanceOf(FakeExplanationJudge::class.java)
        }
    }

    @Test
    fun `provider=fake면 Fake가 유일한 채점기다`() {
        runner.withPropertyValues("bytecs.interview.judge.provider=fake").run { context ->
            assertThat(context).hasSingleBean(ExplanationJudge::class.java)
            assertThat(context.getBean(ExplanationJudge::class.java)).isInstanceOf(FakeExplanationJudge::class.java)
        }
    }

    @Test
    fun `provider=http면 HttpChatExplanationJudge가 유일한 채점기다(운영 실 AI)`() {
        runner.withPropertyValues(
            "bytecs.interview.judge.provider=http",
            "bytecs.interview.judge.base-url=http://localhost:1/v1",
            "bytecs.interview.judge.model=test-model",
        ).run { context ->
            assertThat(context).hasSingleBean(ExplanationJudge::class.java)
            assertThat(context.getBean(ExplanationJudge::class.java)).isInstanceOf(HttpChatExplanationJudge::class.java)
        }
    }
}
