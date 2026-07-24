package watson.bytecs.interview.application.dto

import watson.bytecs.session.application.dto.StreakResponse

/**
 * 면접 세션 답 제출 결과.
 *  - judged: AI 채점 성공 여부. false면 폴백 — points는 빈 목록, comment는 안내 문구, modelAnswer·rubricTexts는 그대로 공개된다.
 *  - points: 채점 성공일 때만 포인트별 충족 체크리스트(무낙인 — 미충족은 비난이 아니라 "보완하면 좋은 포인트"로 클라가 그린다).
 *  - modelAnswer·conceptName: 채점 성공·폴백 관계없이 항상 공개(제출 이후엔 no-leak 제약이 풀린다) — 방금 답한 문항 기준.
 *  - status·position·totalCount·nextQuestion·nextConceptName: 세션 진행 상태. 완료됐으면 둘 다 null.
 *  - nextHintCount: 다음 질문의 전체 힌트 수(SessionAttemptResponse 관례 미러). 새 질문은 공개 0에서 시작하므로
 *    공개 목록(revealedHints)은 싣지 않는다 — 개수만으로 클라가 힌트 진입점 노출 여부를 판단한다. 완료됐으면 null.
 *  - practicedConceptCount: 이 제출로 세션이 완료됐을 때만 채워지는 완료 요약("오늘 개념 N개를 면접처럼 설명해봤어요")의 N.
 *  - streak: 이 제출로 세션이 완료됐을 때만 갱신된 스트릭을 싣는다(DI5 — 일반 세션과 OR로 하루 멱등 충족).
 *  - reviewProblemId: '검증됨' 미달(부분·미검증)일 때만, 그 개념으로 풀었던 주관식 문제의 '다시 보기'(DI10) 대상 id. 검증됨·폴백이면 null.
 *    클라는 이 값이 있을 때만 '그때 푼 문제 다시 보기' 링크를 그린다(`GET /api/learning-history/problems/{id}` 재열람).
 */
data class InterviewAnswerResponse(
    val judged: Boolean,
    val points: List<RubricPointResultResponse>,
    val comment: String,
    val modelAnswer: String,
    val conceptName: String,
    val status: String,
    val position: Int,
    val totalCount: Int,
    val nextQuestion: String?,
    val nextConceptName: String?,
    val nextPromptId: Long?,
    val nextHintCount: Int?,
    val practicedConceptCount: Int?,
    val streak: StreakResponse?,
    val reviewProblemId: Long?,
)
