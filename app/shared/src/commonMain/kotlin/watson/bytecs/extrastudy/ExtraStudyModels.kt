package watson.bytecs.extrastudy

import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.JudgeResult

/**
 * 추가 학습('조금 더 풀기') 슬라이스의 도메인 모델. 백엔드 `/api/extra-study` 계약과 형태를 맞추되,
 * DTO(유선 형태)와 분리해 UI·뷰모델은 이 모델만 본다.
 *
 * ⭐️ 추가 학습은 세션과 **동일한 학습 데이터 의미론**(풀이 이력·숙련도·복습)을 갖되, 목표 분량·완결
 * (완료 카운트·스트릭)이 없는 자유 풀이다. 한 번에 한 문제, 재진입 시 그 문제를 이어 푼다.
 *
 * 판정 결과·심화 정보는 문제 슬라이스와 동일한 결정적 계약이므로 [JudgeResult]·[Enrichment]를 재사용한다
 * (중복 정의 방지 — 공용 leaf 타입).
 */

/**
 * 힌트 하나(무낙인·비노출 계약). 정답을 유추할 정보를 담지 않는다.
 *  - [text]: 힌트 본문. [codeSnippet]: 코드 예시(있을 때만).
 *
 * ⭐️ **미공개 힌트 본문은 절대 여기 실리지 않는다.** 서버는 이미 공개된 힌트만 [ExtraStudyProblem.revealedHints]로
 * 내려주고, 새 공개는 '열기' 요청([ExtraStudyRepository.revealHint])의 응답으로만 받는다(no-leak).
 */
data class ExtraStudyHint(
    val text: String,
    val codeSnippet: String? = null,
)

/**
 * 추가 학습에서 '지금 풀 문제'. 정답을 유추할 정보(개념·허용답·해설)는 담지 않는다(무낙인·비노출).
 *  - [hintCount]: 이 문제의 전체 힌트 수(0이면 진입점을 노출하지 않는다). 미공개 본문은 담지 않는다.
 *  - [revealedHints]: 이미 공개한 힌트 본문(약→강, 재진입 복원용). 아직 안 연 것은 포함되지 않는다.
 */
data class ExtraStudyProblem(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
    val hintCount: Int = 0,
    val revealedHints: List<ExtraStudyHint> = emptyList(),
)

/**
 * `GET /api/extra-study/current`의 도메인 결과. 이어 풀 문제가 있으면 [Available], 모두 풀었고 도래한
 * 복습도 없으면 [Exhausted](소진). 소진은 오류가 아니라 정상 상태다(무낙인·긍정 톤).
 */
sealed interface ExtraStudyState {
    /** 이어 풀 문제가 있음. */
    data class Available(val problem: ExtraStudyProblem) : ExtraStudyState

    /** 소진 — 모든 문제를 풀었고 도래한 복습도 없다. 복습 주기가 돌아오면 자연히 다시 풀 문제가 생긴다. */
    data object Exhausted : ExtraStudyState
}

/**
 * 답 제출 결과.
 *  - [concepts]·[explanation]·[enrichment]·[representativeAnswer]: 정답(CORRECT)일 때만 채워진다(비정답은
 *    no-leak으로 null). 태깅 순서를 보존한 개념 목록 — 첫 번째가 대표 개념이다. [enrichment]는 '더 알아보기'
 *    (§5.7) — 없어도 되는 선택 콘텐츠. [representativeAnswer]는 화면 표시용 대표 정답 하나([2026-07-16] 오너 결정).
 *  - [misconceptionHint]: 비정답이고 제출이 예상 오답(큐레이션됨)과 일치할 때만 채워진다(push·자동, 기능 2.5).
 *    서버는 매칭 시 결과를 MISMATCH로 확정한다(NEAR_MISS보다 우선). 무낙인: 있어도 오답 확정 아님, 정답 비노출.
 *
 * ⭐️ 세션 attempt와 달리 진행(다음 문제)을 응답에 싣지 않는다 — 정답 처리 후 클라가 `getCurrent`를 다시 불러
 * 다음(또는 소진)을 받는다(단일 항목 모델이라 조회를 원천으로 삼는다).
 */
data class ExtraStudyAttempt(
    val result: JudgeResult,
    val concepts: List<String>? = null,
    val explanation: String? = null,
    val enrichment: Enrichment? = null,
    val representativeAnswer: String? = null,
    val misconceptionHint: String? = null,
)

/**
 * 정답 공개(안전판) 결과. 공개 후에도 [representativeAnswer]를 **직접 따라 입력**해야 다음으로 넘어간다.
 *  - [concepts]: 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
 *  - [representativeAnswer]: 화면 표시용 대표 정답 하나 — 허용답 나열은 화면에서 하지 않는다([2026-07-16] 오너 결정).
 *  - [enrichment]: '더 알아보기'(§5.7) — 정답 공개로 학습한 뒤도 정답 접근이 허용된 맥락이라 포함된다.
 */
data class ExtraStudyReveal(
    val concepts: List<String>,
    val explanation: String?,
    val representativeAnswer: String,
    val enrichment: Enrichment? = null,
)

/**
 * 힌트 '열기' 결과. 서버가 원천이다 — 공개 수는 [revealedHints]`.size`로만 센다(클라 로컬 카운터를 신뢰하지 않는다).
 *  - [hintCount]: 전체 힌트 수. [revealedHints]: 지금까지 공개된 전체 목록(약→강).
 */
data class ExtraStudyHintReveal(
    val hintCount: Int,
    val revealedHints: List<ExtraStudyHint>,
)

// ── 타입드 예외 ─────────────────────────────────────────────────────────────
// 서버는 열린(미해결) 항목이 없을 때의 경합을 HTTP 409 + 본문 errorCode=EXTRA_STUDY_NO_OPEN_ITEM로 알린다.
// 저장소가 이를 도메인 예외로 번역해, 뷰모델이 시스템 오류와 구분해 `load()`로 재동기화한다(무낙인).

/** 열린(미해결) 항목이 없을 때 제출·공개를 시도(서버 EXTRA_STUDY_NO_OPEN_ITEM). */
class ExtraStudyNoOpenItemException : Exception("이어 풀 문제를 다시 불러올게요")
