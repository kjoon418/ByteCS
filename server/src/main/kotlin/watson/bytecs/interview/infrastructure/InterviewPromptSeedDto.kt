package watson.bytecs.interview.infrastructure

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * `interview-prompts.json`의 파싱 전용 DTO. 도메인 불변식은 여기서 검증하지 않는다 —
 * [InterviewPromptDataLoader]가 이 DTO를 도메인 생성자([watson.bytecs.interview.domain.InterviewPrompt])로 조립하는
 * 순간 init require가 발동한다. 즉 이 파일은 "JSON 모양"만, "내용이 옳은가"는 도메인이 책임진다(Problem 시드 관례와 동일).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InterviewPromptSeedFile(
    val prompts: List<InterviewPromptSeedDto> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InterviewPromptSeedDto(
    // 이 면접 질문이 귀속되는 개념 이름 — 문제 시드가 만든 기존 개념을 이름으로 참조한다(없으면 로더가 기동을 실패시킨다).
    val concept: String,
    val question: String,
    val modelAnswer: String,
    val rubricPoints: List<String> = emptyList(),
)
