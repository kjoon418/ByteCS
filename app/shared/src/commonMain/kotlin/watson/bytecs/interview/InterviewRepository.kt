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

/**
 * 오늘 이 시점엔 진행할 면접 세션이 없다 — 후보 없음(404)·쿼터 소진(409)·세션 없음/이미 완료(404/409)·회원 전용(403)을
 * 아우른다. 보통은 홈 카드가 진입 전에 거르지만, 카드 상태가 낡았거나(다른 기기·다른 탭에서 소진) 세션이 도중에 끝나면
 * 이 신호가 진입·제출에서 뒤늦게 도달할 수 있다. 재시도로 풀리지 않으므로 뷰모델은 홈으로 되돌린다(홈 카드가 실제 사유로
 * 다시 그려진다). 전송 실패(시스템 오류)와는 구분한다 — 그건 재시도가 의미 있는 별개 경로다.
 */
class InterviewUnavailableException : RuntimeException()

/**
 * 서버가 2xx로 답을 반영(커서 전진)했으나 응답 본문을 해석하지 못했다(직렬화·불변식 위반). 같은 답을 다시 보내면
 * 서버는 이미 다음 문항으로 넘어가 있어 **엉뚱한 문항에 답하게 되므로**, 전송 실패처럼 재제출을 권하면 안 된다.
 * 뷰모델은 세션을 다시 불러와(서버 커서 기준) 복구한다. 전송 실패(재시도 가능)와 구분하려고 별도 타입으로 올린다.
 */
class InterviewResponseMappingException(cause: Throwable) : RuntimeException(cause)
