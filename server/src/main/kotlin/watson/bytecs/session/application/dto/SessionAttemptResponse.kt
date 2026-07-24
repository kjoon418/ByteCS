package watson.bytecs.session.application.dto

import watson.bytecs.problem.application.dto.EnrichmentResponse

/**
 * 세션 답 제출 결과 응답.
 *  - result: 판정(CORRECT/NEAR_MISS/MISMATCH). 비정답도 200으로 응답한다(무낙인).
 *  - concepts·explanation·enrichment·representativeAnswer: 정답(CORRECT)일 때만 채워진다(concepts는 태깅 순). 비정답에서는 no-leak으로 null.
 *    representativeAnswer(화면 표시용 대표 정답)는 정답 시 항상 실리고, enrichment(심화 정보·'더 알아보기')는 문제에 없으면 정답이어도 null(graceful).
 *  - currentProblem: 정답으로 전진한 뒤 지금 풀 무낙인 문제. 세션이 완료됐으면 null.
 *  - misconceptionHint: 비정답이고 제출이 예상 오답 집합과 정규화 후 일치할 때만 채워진다(그 외 null).
 *    실려도 오답으로 확정되지 않고(무낙인) 정답을 노출하지 않으며, 이때 result는 MISMATCH로 확정된다(근접보다 우선).
 *  - streak: 이 제출로 세션이 완료됐을 때만 갱신된 스트릭을 싣는다(그 외 null).
 *  - needsDifficultyPrompt: 이 제출로 완료됐고 완료 화면에서 난이도 제안을 노출해야 할 때만 true다(선호 미설정 && 미응답).
 *    미완료 제출에서는 항상 false다(제안은 완료 화면에서만 뜬다).
 *  - unlockedIntegrations: 이 제출로 세션이 완료되며 **새로 열린** 지정 연결 문제들(계획 §3.2 · D2). 미완료·해제 없음이면 빈 목록.
 *    각 항목은 구성 개념명 목록이며, 완료 화면이 잠금 해제 배지로 담백하게 안내한다(디자인 04의 4-b).
 *  - newlyEligibleConcepts: 이 **정답**으로 처음 면접 후보가 된 개념명들(DI9 · 디자인 03의 9-b). 레벨이 승급 임계 미만→이상으로
 *    올라갔고 그 개념에 승인된 면접 질문이 있을 때만 채워진다(완료 여부와 무관 — 정답 순간 알림). 이미 열려 있던 개념을 다시
 *    맞히거나 도움 정답으로 임계 미달이면 빈 목록. 03 정답 화면이 승급 인라인 라인(PromotionInlineLine)으로 담백하게 안내한다.
 */
data class SessionAttemptResponse(
    val result: String,
    val status: String,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val concepts: List<String>?,
    val explanation: String?,
    val enrichment: EnrichmentResponse?,
    val representativeAnswer: String?,
    val misconceptionHint: String?,
    val currentProblem: SessionProblemResponse?,
    val streak: StreakResponse?,
    val needsDifficultyPrompt: Boolean,
    val unlockedIntegrations: List<UnlockedIntegrationResponse>,
    val newlyEligibleConcepts: List<String>,
)
