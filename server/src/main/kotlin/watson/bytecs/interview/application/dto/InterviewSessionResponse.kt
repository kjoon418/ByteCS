package watson.bytecs.interview.application.dto

import java.time.LocalDate

/**
 * 면접 세션 상태 응답(생성·재개 조회 공통). currentQuestion·currentConceptName은 질문 문구·개념명(맥락)만 싣는다 —
 * 모범 설명·루브릭은 no-leak으로 제출 후에만 공개한다([InterviewAnswerResponse]). currentPromptId는 클라이언트
 * 화면의 재구성 키(포커스 등)로만 쓰인다 — 콘텐츠 신고 대상 지정은 아직 없다([review-todo], InterviewSessionScreen 참고).
 */
data class InterviewSessionResponse(
    val sessionId: Long,
    val sessionDate: LocalDate,
    val status: String,
    val position: Int,
    val totalCount: Int,
    val currentQuestion: String?,
    val currentConceptName: String?,
    val currentPromptId: Long?,
)
