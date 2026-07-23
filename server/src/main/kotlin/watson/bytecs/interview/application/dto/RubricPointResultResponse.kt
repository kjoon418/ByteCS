package watson.bytecs.interview.application.dto

/** 루브릭 핵심 포인트 하나와, 이번 제출이 그 포인트를 짚었는지(무낙인 체크리스트 렌더용). */
data class RubricPointResultResponse(
    val text: String,
    val satisfied: Boolean,
)
