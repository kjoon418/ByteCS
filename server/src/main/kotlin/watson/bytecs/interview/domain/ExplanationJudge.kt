package watson.bytecs.interview.domain

/**
 * AI 루브릭 채점기(계획 §3.3·§4.4). 루브릭 포인트 목록과 사용자의 자유 설명을 받아, 포인트별 충족 여부만 판정한다
 * (자유 평가·지식 공급 없음 — "루브릭 대조만"). 실패(타임아웃·오류·응답 파싱 실패)는 null을 돌려주고,
 * 호출부([watson.bytecs.interview.application.InterviewSessionService])가 이를 폴백으로 처리한다(모범 설명 공개·준비도 미갱신).
 * 구현은 local/test에서 결정적 Fake([watson.bytecs.interview.infrastructure.FakeExplanationJudge])를 쓰고,
 * 실 AI 연동은 C3에서 별도 구현체로 대체한다(그 프로파일 분기는 C3 소관 — [review-todo]).
 */
interface ExplanationJudge {

    /** [rubricPoints]와 같은 순서·크기의 충족 여부 목록을 돌려준다. 실패 시 null(호출부가 폴백 처리). */
    fun judge(rubricPoints: List<String>, explanation: String): JudgeResult?
}

/** 포인트별 충족 여부와, 결과 화면에 실을 무낙인 코멘트 한 줄. */
data class JudgeResult(
    val satisfiedPoints: List<Boolean>,
    val comment: String,
)
