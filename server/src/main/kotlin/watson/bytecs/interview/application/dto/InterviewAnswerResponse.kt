package watson.bytecs.interview.application.dto

import watson.bytecs.session.application.dto.StreakResponse

/**
 * 면접 세션 답 제출 결과.
 *  - judged: AI 채점 성공 여부. false면 폴백 — points는 빈 목록, comment는 안내 문구, modelAnswer·rubricTexts는 그대로 공개된다.
 *  - points: 채점 성공일 때만 포인트별 충족 체크리스트(무낙인 — 미충족은 비난이 아니라 "보완하면 좋은 포인트"로 클라가 그린다).
 *  - modelAnswer: 채점 성공·폴백 관계없이 항상 공개(제출 이후엔 no-leak 제약이 풀린다).
 *  - status·position·totalCount·nextQuestion: 세션 진행 상태. 완료됐으면 nextQuestion은 null.
 *  - streak: 이 제출로 세션이 완료됐을 때만 갱신된 스트릭을 싣는다(DI5 — 일반 세션과 OR로 하루 멱등 충족).
 */
data class InterviewAnswerResponse(
    val judged: Boolean,
    val points: List<RubricPointResultResponse>,
    val comment: String,
    val modelAnswer: String,
    val status: String,
    val position: Int,
    val totalCount: Int,
    val nextQuestion: String?,
    val streak: StreakResponse?,
)
