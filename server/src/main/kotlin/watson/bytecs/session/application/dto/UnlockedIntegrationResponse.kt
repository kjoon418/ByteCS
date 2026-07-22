package watson.bytecs.session.application.dto

/**
 * 세션 완료로 새로 열린 지정 연결 문제 하나의 안내(계획 §3.2 · D2, 디자인 04의 4-b).
 * 화면 문구가 개념명 기반("○○·△△를 모두 배웠어요")이라 **구성 개념명 목록만** 싣는다(태깅 순 — 대표 개념이 먼저).
 * 문제 지문은 싣지 않는다: 풀기 전 선노출은 불필요하고 no-leak 보수 원칙에 맞지 않는다(리드 결정 2026-07-22).
 */
data class UnlockedIntegrationResponse(
    val concepts: List<String>,
)
