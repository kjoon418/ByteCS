package watson.bytecs.session.domain

/**
 * 일일 세션의 상태. 분량(본 문제)을 모두 정답으로 마치면 IN_PROGRESS→COMPLETED로 단방향 전이한다.
 * 시간 경과로는 종료되지 않는다(분량 기반·무낙인).
 */
enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
}
