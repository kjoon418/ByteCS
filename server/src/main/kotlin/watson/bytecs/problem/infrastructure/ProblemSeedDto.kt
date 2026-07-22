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
    /**
     * 개념 이름 → [watson.bytecs.problem.domain.ProblemCategory] 이름(문자열)의 전역 매핑.
     * 카테고리는 개념에 귀속되는 단일 출처이므로(명세 §7), 문제마다가 아니라 파일 전체에 한 번만 선언한다.
     * 이 맵에 없는 개념 이름은 미분류(null)로 남는다 — 카테고리 필드 도입 이전 시드와의 하위 호환.
     */
    val conceptCategories: Map<String, String?> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProblemSeedDto(
    val question: String,
    val type: String? = null,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
    val concepts: List<String> = emptyList(),
    // 연결 문제 여부(DI12). 생략 시 false — 기존 시드(problems-generated.json 등)를 무변경으로 로드하는 하위 호환.
    val integration: Boolean = false,
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
