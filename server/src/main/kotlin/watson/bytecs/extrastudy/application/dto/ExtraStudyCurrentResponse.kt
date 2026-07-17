package watson.bytecs.extrastudy.application.dto

/**
 * 추가 학습의 현재(이어 풀) 상태(GET /api/extra-study/current) 응답.
 * 풀 문제가 있으면 exhausted=false·problem 채움, 모두 풀었고 도래 복습도 없으면 exhausted=true·problem=null(소진 안내).
 * 소진은 오류가 아니라 정상 상태다 — 무낙인·긍정 톤 카피는 클라가 담당한다.
 */
data class ExtraStudyCurrentResponse(
    val exhausted: Boolean,
    val problem: ExtraStudyProblemResponse?,
)
