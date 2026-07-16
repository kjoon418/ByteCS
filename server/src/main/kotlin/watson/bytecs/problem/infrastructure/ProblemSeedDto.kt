package watson.bytecs.problem.infrastructure

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * `problems-generated.json`의 파싱 전용 DTO들.
 * 도메인 불변식은 여기서 검증하지 않는다 — [ProblemDataLoader]가 이 DTO를 도메인 생성자로 조립하는
 * 순간 [watson.bytecs.problem.domain.Problem]·[watson.bytecs.problem.domain.Concept] 등의 init require가 전부 발동한다.
 * 즉, 이 파일은 "JSON 모양"만 책임지고, "내용이 옳은가"는 도메인이 책임진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProblemSeedFile(
    val problems: List<ProblemSeedDto> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProblemSeedDto(
    val question: String,
    val type: String? = null,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
    val concepts: List<String> = emptyList(),
    val acceptableAnswers: List<String> = emptyList(),
    val representativeAnswer: String,
    val explanation: String? = null,
    val hints: List<HintDto> = emptyList(),
    val misconceptionHints: List<MisconceptionHintDto> = emptyList(),
    val enrichment: EnrichmentDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HintDto(
    val text: String,
    val codeSnippet: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MisconceptionHintDto(
    val expectedAnswers: List<String> = emptyList(),
    val message: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnrichmentDto(
    val title: String,
    val body: String,
    val items: List<EnrichmentItemDto> = emptyList(),
    val quote: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnrichmentItemDto(
    val title: String,
    val description: String,
)
