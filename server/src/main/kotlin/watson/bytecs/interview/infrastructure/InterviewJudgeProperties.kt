package watson.bytecs.interview.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 면접 설명 채점기(C3) 설정. 공급자 중립 — OpenAI 호환 `/chat/completions` 엔드포인트를 쓰는 어떤 LLM이든(로컬 CLI LLM,
 * Gemini의 OpenAI 호환 엔드포인트, OpenAI 등) [baseUrl]·[model]·[apiKey]만 바꿔 붙일 수 있다(계획 §4.4 "환경변수로 교체 가능").
 *
 * - [provider]=`fake`(기본): 결정적 [FakeExplanationJudge] — 네트워크 호출 0(로컬 오프라인·`:server:test`).
 * - [provider]=`http`: [HttpChatExplanationJudge] — [baseUrl]의 OpenAI 호환 API를 호출한다.
 *
 * 비용 정책: 로컬은 `fake`(무료) 또는 로컬 CLI LLM(무료)로 두고, 실배포만 저비용 클라우드 모델을 쓴다(오너 결정 2026-07-24 —
 * 큐레이터 합격 검토가 별도로 있어 채점 모델 성능 요구가 낮으므로 Gemini 2.0 Flash 같은 저비용 모델 사용).
 */
@ConfigurationProperties(prefix = "bytecs.interview.judge")
data class InterviewJudgeProperties(
    /** `fake` | `http`. 기본은 `fake`(무료·오프라인) — 실호출은 명시적으로 `http`를 켠 프로파일에서만 일어난다. */
    val provider: String = "fake",
    /** OpenAI 호환 API 베이스 URL(끝에 `/chat/completions`를 붙여 호출). 예 로컬: `http://localhost:11434/v1`, Gemini: `https://generativelanguage.googleapis.com/v1beta/openai`. */
    val baseUrl: String = "",
    /** 모델 이름. 예 로컬: `qwen2.5`, Gemini: `gemini-2.0-flash`. */
    val model: String = "",
    /** Bearer 토큰(API 키). 비어 있으면 Authorization 헤더를 붙이지 않는다(로컬 CLI LLM은 보통 키가 필요 없다). */
    val apiKey: String = "",
    /** 채점은 결정적이어야 하므로 낮은 온도를 쓴다(계획 §4.4). */
    val temperature: Double = 0.0,
    /** 연결·읽기 타임아웃(ms). 죽은 엔드포인트에서 폴백까지 오래 매달리지 않게 상한을 둔다. */
    val timeoutMs: Long = 20_000,
    /** `response_format: {type: json_object}` 강제 여부. Gemini·OpenAI·Ollama는 지원. 이를 거부하는 공급자면 false로 끈다. */
    val jsonMode: Boolean = true,
)
