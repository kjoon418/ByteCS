package watson.bytecs.extrastudy.application.dto

/**
 * 추가 학습에서 이미 공개한 힌트 하나. 미공개 힌트 본문은 절대 담기지 않는다(no-leak — 공개분만 싣는다).
 * 세션 RevealedHintResponse와 동형이되, 슬라이스 결합을 피하려 동형 신설한다(세션이 problem의 leaf만 공유하는 기존 결).
 */
data class ExtraStudyRevealedHintResponse(
    val text: String,
    val codeSnippet: String?,
)
