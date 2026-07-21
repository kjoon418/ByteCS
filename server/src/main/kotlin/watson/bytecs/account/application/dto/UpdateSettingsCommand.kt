package watson.bytecs.account.application.dto

import watson.bytecs.problem.domain.Difficulty

/**
 * 학습 설정 부분 갱신 커맨드(서비스 계층 입력 — 웹 DTO에 의존하지 않는다).
 * 각 필드는 '이번 요청에서 바꿀 것'만 담는다: null/false이면 해당 설정은 손대지 않는다.
 *  - dailySessionSize: 지정 시 일일 세션 분량을 바꾼다.
 *  - preferredDifficulty: 지정 시 선호 난이도를 설정한다(도메인 규칙상 제안 노출도 종료).
 *  - resetPreferredDifficulty: true면 선호 난이도를 미설정(자동)으로 되돌린다(제안 응답 상태는 되돌리지 않는다).
 *  - markDifficultyPromptDone: true면 완료 화면 제안에 응답(거절 포함)했음을 기록한다.
 * 선호 난이도 '설정'과 '되돌리기'는 같은 필드에 대한 반대 조작이라 동시 지정은 모순이다(생성 시 거부 → 400).
 */
data class UpdateSettingsCommand(
    val dailySessionSize: Int?,
    val preferredDifficulty: Difficulty?,
    val resetPreferredDifficulty: Boolean,
    val markDifficultyPromptDone: Boolean,
) {
    init {
        require(!(preferredDifficulty != null && resetPreferredDifficulty)) {
            "선호 난이도 설정과 미설정 되돌리기를 동시에 요청할 수 없습니다."
        }
    }
}
