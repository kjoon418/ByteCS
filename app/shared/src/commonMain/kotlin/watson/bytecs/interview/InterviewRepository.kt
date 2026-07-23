package watson.bytecs.interview

/**
 * 면접 세션 데이터 접근 계약(계획 §4.3). 후보 산정·채점·상태 전이는 전적으로 서버에 위임한다 —
 * 특히 AI 루브릭 채점([submitExplanation])은 서버만 판정하고, 클라이언트는 결과를 그릴 뿐이다.
 * 인증 헤더 부착은 HTTP 클라이언트가 담당하므로 저장소는 토큰을 직접 다루지 않는다.
 *
 * 실행 앱은 [watson.bytecs.interview.data.KtorInterviewRepository](C4-β)가 주입된다. 테스트는
 * [watson.bytecs.interview.FakeInterviewRepository]로 이 계약을 결정적으로 만족시킨다.
 */
interface InterviewRepository {
    /**
     * 홈(02 3-b) 진입 카드용 상태(후보 수·오늘 잔여 쿼터·게스트 여부). `GET /api/interview/status`.
     * 게스트에게도 허용된다(가입 유도 데이터).
     */
    suspend fun status(): InterviewStatus

    /**
     * 오늘의 면접 세션을 시작하거나(없으면 생성) 진행 중이면 그대로 받는다(중단·재개). `POST /api/interview/sessions/today`.
     * 회원 전용·쿼터 제한은 홈 카드가 진입 전에 거르므로, 진입은 후보 1개 이상·쿼터 남음을 전제한다.
     */
    suspend fun startOrResumeToday(): InterviewSession

    /**
     * 현재 문항에 자기 말로 쓴 설명을 제출하고 채점 결과를 받는다. `POST /api/interview/sessions/today/answers`.
     * [position]은 답할 칸(0-based). 재제출은 없다(1문항 1채점 — 재도전은 다음 날 새 세션).
     * 채점 실패(폴백)는 예외가 아니라 [ExplanationOutcome.result]의 `fallback=true`로 온다(모범 설명 포함).
     */
    suspend fun submitExplanation(position: Int, text: String): ExplanationOutcome
}
