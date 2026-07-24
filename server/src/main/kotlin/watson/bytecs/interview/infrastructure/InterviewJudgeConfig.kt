package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import watson.bytecs.interview.domain.ExplanationJudge

/**
 * 면접 채점기(C3) 배선. [InterviewJudgeProperties.provider] 값으로 구현체를 고른다:
 *  - `http`  → 여기서 [HttpChatExplanationJudge]를 만든다(OpenAI 호환 API 실호출).
 *  - `fake`(기본, 미설정 포함) → [FakeExplanationJudge]가 활성(그 클래스의 @ConditionalOnProperty, matchIfMissing=true).
 * 두 조건은 상호 배타라 [ExplanationJudge] 빈은 항상 정확히 하나다. `:server:test`·기본 프로파일은 provider 미설정 → Fake라 실호출 0.
 */
@Configuration
@EnableConfigurationProperties(InterviewJudgeProperties::class)
class InterviewJudgeConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bytecs.interview.judge", name = ["provider"], havingValue = "http")
    fun interviewJudgeRestClient(
        properties: InterviewJudgeProperties,
        builder: RestClient.Builder,
    ): RestClient {
        val timeoutMillis = properties.timeoutMs.toInt()
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(timeoutMillis)
            setReadTimeout(timeoutMillis)
        }
        return builder
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    @Bean
    @ConditionalOnProperty(prefix = "bytecs.interview.judge", name = ["provider"], havingValue = "http")
    fun httpExplanationJudge(
        interviewJudgeRestClient: RestClient,
        properties: InterviewJudgeProperties,
        objectMapper: ObjectMapper,
    ): ExplanationJudge = HttpChatExplanationJudge(interviewJudgeRestClient, properties, objectMapper)
}
