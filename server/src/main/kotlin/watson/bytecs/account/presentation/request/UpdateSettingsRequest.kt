package watson.bytecs.account.presentation.request

import watson.bytecs.problem.domain.Difficulty

/**
 * 학습 설정 변경 요청(부분 갱신). 보낸 필드만 반영하고, 보내지 않은(null) 필드는 기존 값을 유지한다.
 *  - dailySessionSize: 일일 세션 분량. 허용 범위는 도메인(UserSettings)이 강제한다.
 *  - preferredDifficulty: 선호 난이도. 지정 시 완료 화면 제안 노출도 종료된다(도메인 규칙). 값이 enum이 아니면 역직렬화 단계에서 400.
 *  - resetPreferredDifficulty: 선호 난이도를 미설정(자동)으로 되돌린다. `true`만 의미가 있다. preferredDifficulty와 동시 지정은 모순이라 400.
 *    (플레인 Jackson은 '필드 부재'와 '명시적 null'을 구분하지 못하므로, difficultyPromptDone과 같은 전용 액션 플래그로 리셋을 표현한다.)
 *  - difficultyPromptDone: 완료 화면 제안 거절 기록용. `true`만 의미가 있다(응답했음 = 다시 묻지 않음).
 * 부분 갱신이므로 개별 필드의 null 여부는 검증하지 않는다(빈 본문은 무연산). 값 자체의 유효성은 도메인·역직렬화가 강제한다.
 */
data class UpdateSettingsRequest(
    val dailySessionSize: Int?,
    val preferredDifficulty: Difficulty?,
    val resetPreferredDifficulty: Boolean?,
    val difficultyPromptDone: Boolean?,
)
