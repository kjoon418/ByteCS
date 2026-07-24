package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import watson.bytecs.interview.domain.ExplanationJudge
import watson.bytecs.interview.domain.JudgeResult

/**
 * 실 AI 루브릭 채점기(C3, 계획 §3.3·§4.4). OpenAI 호환 `/chat/completions`로 "사용자의 설명이 각 루브릭 포인트를 짚었는가"를
 * 포인트별 boolean으로만 판정한다 — 자유 평가·지식 공급은 시키지 않는다(채점 기준은 전부 큐레이션 콘텐츠).
 *
 * 공급자 중립: [InterviewJudgeProperties]의 baseUrl/model/apiKey만 바꾸면 로컬 CLI LLM(Ollama 등)·Gemini(OpenAI 호환)·OpenAI에
 * 그대로 붙는다. 응답은 JSON 한 덩어리로 강제하고, 파싱·형태 검증에 실패하면 **null(폴백)** 로 돌린다(계획 §3.3 graceful) —
 * 호출부([watson.bytecs.interview.application.InterviewSessionService])가 모범 설명 공개·준비도 미갱신으로 처리한다.
 * 실패 시 1회 재시도한다(계획 부록 "채점 재시도 1회").
 */
class HttpChatExplanationJudge(
    private val restClient: RestClient,
    private val properties: InterviewJudgeProperties,
    private val objectMapper: ObjectMapper,
) : ExplanationJudge {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun judge(rubricPoints: List<String>, explanation: String): JudgeResult? {
        require(rubricPoints.isNotEmpty()) { "루브릭 포인트가 하나 이상 있어야 합니다." }
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val result = callOnce(rubricPoints, explanation)
                if (result != null) {
                    return result
                }
                log.warn("면접 채점 응답을 해석하지 못했습니다(attempt {}/{}) — 폴백 후보.", attempt + 1, MAX_ATTEMPTS)
            } catch (e: Exception) {
                log.warn("면접 채점 호출 실패(attempt {}/{}): {}", attempt + 1, MAX_ATTEMPTS, e.message)
            }
        }
        return null
    }

    private fun callOnce(rubricPoints: List<String>, explanation: String): JudgeResult? {
        val body = buildRequestBody(rubricPoints, explanation)
        val raw = restClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .headers { headers ->
                if (properties.apiKey.isNotBlank()) {
                    headers.setBearerAuth(properties.apiKey)
                }
            }
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: return null

        val content = objectMapper.readTree(raw)
            .path("choices").path(0).path("message").path("content").asText("")
        if (content.isBlank()) {
            return null
        }
        return parseJudgeResult(content, rubricPoints)
    }

    private fun buildRequestBody(rubricPoints: List<String>, explanation: String): Map<String, Any> =
        buildMap {
            put("model", properties.model)
            put("temperature", properties.temperature)
            put(
                "messages",
                listOf(
                    mapOf("role" to "system", "content" to systemPrompt(rubricPoints.size)),
                    mapOf("role" to "user", "content" to userPrompt(rubricPoints, explanation)),
                ),
            )
            if (properties.jsonMode) {
                put("response_format", mapOf("type" to "json_object"))
            }
        }

    /** 모델 응답 문자열에서 `{"satisfied":[..], "comment":".."}`를 뽑아 검증한다. 형태가 어긋나면 null(폴백). */
    private fun parseJudgeResult(content: String, rubricPoints: List<String>): JudgeResult? {
        val json = extractJsonObject(content) ?: return null
        val parsed: JsonNode = try {
            objectMapper.readTree(json)
        } catch (e: Exception) {
            return null
        }

        val satisfiedNode = parsed.path("satisfied")
        if (!satisfiedNode.isArray || satisfiedNode.size() != rubricPoints.size) {
            return null
        }
        val satisfied = satisfiedNode.map { it.asBoolean(false) }
        val comment = parsed.path("comment").asText("").ifBlank { defaultComment(satisfied) }
        return JudgeResult(satisfiedPoints = satisfied, comment = comment)
    }

    /** 코드펜스(```json …```)나 앞뒤 잡소리가 섞여 와도 첫 `{`부터 마지막 `}`까지를 JSON 후보로 본다. */
    private fun extractJsonObject(content: String): String? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        return if (start in 0 until end) content.substring(start, end + 1) else null
    }

    private fun defaultComment(satisfied: List<Boolean>): String {
        val hit = satisfied.count { it }
        return "짚은 포인트 ${hit}개 / 전체 ${satisfied.size}개예요."
    }

    private fun systemPrompt(pointCount: Int): String = """
        당신은 컴퓨터 과학 면접 연습 앱의 '루브릭 대조 채점자'입니다.
        번호가 매겨진 루브릭 포인트 목록과 사용자가 자기 말로 쓴 설명이 주어집니다.
        당신의 유일한 임무: 각 루브릭 포인트에 대해, 사용자의 설명이 그 포인트를 짚었는지(true) 아닌지(false)만 판정합니다.
        루브릭 포인트 밖의 것을 평가하거나, 지식을 덧붙이거나, 전반적 품질/합불을 매기지 마세요.
        오직 아래 형태의 JSON 하나만, 다른 텍스트 없이 출력하세요:
        {"satisfied": [<루브릭 순서대로 포인트별 boolean>], "comment": "<무낙인 한국어 한 문장>"}
        "satisfied" 배열은 정확히 $pointCount 개의 boolean을, 주어진 순서대로 담아야 합니다.
        "comment"는 점수/합불 선고가 아니라 무낙인 톤이어야 합니다 — 미충족 포인트는 '보완하면 좋은 포인트'로 표현하고 비난하지 마세요.
    """.trimIndent()

    private fun userPrompt(rubricPoints: List<String>, explanation: String): String {
        val numbered = rubricPoints.mapIndexed { index, point -> "${index + 1}. $point" }.joinToString("\n")
        return """
            루브릭 포인트 (총 ${rubricPoints.size}개):
            $numbered

            사용자 설명:
            ""${'"'}
            $explanation
            ""${'"'}
        """.trimIndent()
    }

    private companion object {
        const val MAX_ATTEMPTS = 2 // 최초 1회 + 재시도 1회(계획 부록)
    }
}
