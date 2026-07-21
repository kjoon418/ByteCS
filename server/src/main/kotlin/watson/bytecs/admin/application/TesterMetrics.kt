package watson.bytecs.admin.application

/**
 * 테스터 기간 지표(관리자 통계 페이지 표시용 읽기 모델).
 * 앞의 세 값이 요구 지표이고, 뒤의 둘은 규모를 가늠하기 위한 참고 값이다.
 */
data class TesterMetrics(
    /** 지표 1 — 풀이 화면에 진입해 문제 풀이를 시작한 DISTINCT 사용자 수. */
    val startedUserCount: Long,
    /** 지표 2 — 세션(오늘의 한입)을 완료한 DISTINCT 사용자 수. */
    val completedUserCount: Long,
    /** 지표 3 — 세션 완료 후 '조금 더 풀기'로 추가 학습까지 진입한 DISTINCT 사용자 수. */
    val studiedMoreUserCount: Long,
    /** 참고 — 전체 사용자 수(게스트 포함). */
    val totalUserCount: Long,
    /** 참고 — 지금까지 만들어진 전체 세션 수. */
    val totalSessionCount: Long,
)
