package watson.bytecs.admin.application

/**
 * 테스터 기간 지표(관리자 통계 페이지 표시용 읽기 모델).
 * 앞의 세 값이 요구 지표이고, 그 뒤 둘은 규모 참고 값이며, 마지막 둘은 난이도 조절(1차) 운영 지표다.
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
    /** 난이도 지표 ① — 학습자의 선호 난이도 분포(미설정 포함, ADMIN 제외). 쉬움·보통·어려움·미설정 순으로 항상 4행. */
    val preferredDifficultyDistribution: List<PreferredDifficultyStat>,
    /** 난이도 지표 ② — 난이도별 정답 공개(포기)율(2차 적응 조정 임계값 검증 근거). 쉬움·보통·어려움 순으로 항상 3행. */
    val difficultyRevealRates: List<DifficultyRevealStat>,
)

/** 선호 난이도 분포 한 행(표시 라벨·인원). */
data class PreferredDifficultyStat(
    val label: String,
    val count: Long,
)

/** 난이도별 정답 공개율 한 행(표시 라벨·푼 수·정답 공개 수·공개율%). 푼 수가 0이면 공개율은 0으로 둔다. */
data class DifficultyRevealStat(
    val label: String,
    val solvedCount: Long,
    val revealedCount: Long,
    val revealRatePercent: Int,
)
