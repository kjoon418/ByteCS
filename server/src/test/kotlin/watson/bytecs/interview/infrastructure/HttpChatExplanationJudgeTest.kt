package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * OpenAI 호환 채점 어댑터를 MockRestServiceServer로 검증한다 — 실 API 호출 0(`:server:test` 무비용).
 * 계약: 요청은 `/chat/completions`에 model·temperature·JSON 강제·(키 있으면) Bearer로 나가고,
 * 응답은 `{"satisfied":[..],"comment":".."}`를 뽑아 JudgeResult로, 형태가 어긋나거나 오류면 재시도 후 null(폴백)이다.
 */
class HttpChatExplanationJudgeTest {

    private val objectMapper = ObjectMapper()
    private val baseUrl = "http://judge.test/v1"

    private fun properties(apiKey: String = "secret") = InterviewJudgeProperties(
        provider = "http",
        baseUrl = baseUrl,
        model = "test-model",
        apiKey = apiKey,
        temperature = 0.0,
        jsonMode = true,
    )

    private fun newFixture(props: InterviewJudgeProperties = properties()): Pair<HttpChatExplanationJudge, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(baseUrl)
        val server = MockRestServiceServer.bindTo(builder).build()
        val restClient = builder.build()
        return HttpChatExplanationJudge(restClient, props, objectMapper) to server
    }

    /** OpenAI 채팅 응답 껍데기에 모델이 낸 content(JSON 문자열)를 담는다. */
    private fun chatResponse(content: String): String =
        objectMapper.writeValueAsString(
            mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))),
        )

    @Test
    fun `채점 성공 응답을 포인트별 충족 여부와 코멘트로 파싱한다`() {
        val (judge, server) = newFixture()
        server.expect(requestTo("$baseUrl/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer secret"))
            .andExpect(jsonPath("$.model").value("test-model"))
            .andExpect(jsonPath("$.temperature").value(0.0))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andRespond(
                withSuccess(
                    chatResponse("""{"satisfied":[true,false],"comment":"핵심을 잘 짚었어요"}"""),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = judge.judge(listOf("포인트A", "포인트B"), "제 설명입니다")

        assertThat(result).isNotNull
        assertThat(result!!.satisfiedPoints).containsExactly(true, false)
        assertThat(result.comment).isEqualTo("핵심을 잘 짚었어요")
        server.verify()
    }

    @Test
    fun `코드펜스로 감싼 JSON도 파싱한다`() {
        val (judge, server) = newFixture()
        server.expect(requestTo("$baseUrl/chat/completions"))
            .andRespond(
                withSuccess(
                    chatResponse("```json\n{\"satisfied\":[true],\"comment\":\"좋아요\"}\n```"),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = judge.judge(listOf("포인트A"), "설명")

        assertThat(result).isNotNull
        assertThat(result!!.satisfiedPoints).containsExactly(true)
        server.verify()
    }

    @Test
    fun `코멘트가 비어 있으면 무낙인 기본 코멘트로 채운다`() {
        val (judge, server) = newFixture()
        server.expect(requestTo("$baseUrl/chat/completions"))
            .andRespond(
                withSuccess(
                    chatResponse("""{"satisfied":[true,false,false],"comment":""}"""),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = judge.judge(listOf("A", "B", "C"), "설명")

        assertThat(result).isNotNull
        assertThat(result!!.comment).isEqualTo("짚은 포인트 1개 / 전체 3개예요.")
    }

    @Test
    fun `satisfied 길이가 루브릭 수와 다르면 재시도 후 null로 폴백한다`() {
        val (judge, server) = newFixture()
        // 형태 어긋남(길이 1 vs 루브릭 2) → callOnce가 null → 1회 재시도 → 총 2회 호출 후 폴백.
        server.expect(ExpectedCount.times(2), requestTo("$baseUrl/chat/completions"))
            .andRespond(
                withSuccess(
                    chatResponse("""{"satisfied":[true],"comment":"x"}"""),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = judge.judge(listOf("A", "B"), "설명")

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `HTTP 오류면 1회 재시도 후 null로 폴백한다`() {
        val (judge, server) = newFixture()
        server.expect(ExpectedCount.times(2), requestTo("$baseUrl/chat/completions"))
            .andRespond(withServerError())

        val result = judge.judge(listOf("A"), "설명")

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun `API 키가 없으면 Authorization 헤더를 붙이지 않는다`() {
        val (judge, server) = newFixture(properties(apiKey = ""))
        server.expect(requestTo("$baseUrl/chat/completions"))
            .andExpect(headerDoesNotExist("Authorization"))
            .andRespond(
                withSuccess(
                    chatResponse("""{"satisfied":[true],"comment":"ok"}"""),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = judge.judge(listOf("A"), "설명")

        assertThat(result).isNotNull
        server.verify()
    }
}
