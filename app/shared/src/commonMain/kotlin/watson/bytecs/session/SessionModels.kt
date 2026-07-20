package watson.bytecs.session

import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.JudgeResult

/**
 * 일일 세션('오늘의 한입') 슬라이스의 도메인 모델. 백엔드 `/api/sessions` 계약과 형태를 맞추되,
 * DTO(유선 형태)와 분리해 UI·뷰모델은 이 모델만 본다.
 *
 * 판정 결과는 문제 슬라이스와 동일한 결정적 계약이므로 [JudgeResult]를 재사용한다(중복 정의 방지).
 */

/** 세션 상태. 분량(본 문제)을 모두 정답으로 마치면 IN_PROGRESS→COMPLETED로 단방향 전이한다(시간 종료 없음). */
enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ;

    companion object {
        /** 서버 문자열을 대소문자 무시로 매핑. 미지값은 진행 중으로 보수적 처리(막다른 길 방지). */
        fun from(raw: String): SessionStatus =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: IN_PROGRESS
    }
}

/**
 * 힌트 하나(무낙인·비노출 계약). 정답을 유추할 정보를 담지 않는다.
 *  - [text]: 힌트 본문. [codeSnippet]: 코드 예시(있을 때만).
 *
 * ⭐️ **미공개 힌트 본문은 절대 여기 실리지 않는다.** 서버는 이미 공개된 힌트만 [SessionProblem.revealedHints]로
 * 내려주고, 새 공개는 '열기' 요청([SessionRepository.revealHint])의 응답으로만 받는다(no-leak, 인수인계 §3.3).
 */
data class SessionHint(
    val text: String,
    val codeSnippet: String? = null,
)

/**
 * 세션에서 '지금 풀 문제'. 정답을 유추할 정보(개념·허용답·해설)는 담지 않는다(무낙인·비노출).
 *  - [hintCount]: 이 문제의 전체 힌트 수(0이면 진입점을 노출하지 않는다). 미공개 본문은 담지 않는다.
 *  - [revealedHints]: 이미 공개한 힌트 본문(약→강, 재진입 복원용). 아직 안 연 것은 포함되지 않는다.
 *  - [category]: 대표 분류(도메인 명세 §7, 8개 고정 대분류 enum name). 개념명과 달리 정답을 스포일하는
 *    위험이 낮아 **풀기 전부터** 실린다(no-leak 규칙과 독립). 대표 개념이 미분류면 null(=배지 미표시).
 *  - [wrongAttemptCount]: 이 칸에 누적된 비정답(불일치·근접) 횟수(D2 — 재시도 안내의 근거). 서버가
 *    원천이라 재진입해도 정확하다(GET /today·POST /attempts 응답 모두에 실린다).
 */
data class SessionProblem(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
    val hintCount: Int = 0,
    val revealedHints: List<SessionHint> = emptyList(),
    val category: String? = null,
    val wrongAttemptCount: Int = 0,
)

/**
 * 힌트 '열기' 결과. 서버가 원천이다 — 공개 수는 [revealedHints]`.size`로만 센다(클라 로컬 카운터를 신뢰하지 않는다).
 *  - [hintCount]: 전체 힌트 수. [revealedHints]: 지금까지 공개된 전체 목록(약→강).
 */
data class HintReveal(
    val hintCount: Int,
    val revealedHints: List<SessionHint>,
)

/**
 * 오늘의 세션 상태. Home(02)과 세션 풀이(03)의 진입 상태.
 *  - [position]: 지금 풀어야 할 칸(=완료한 본 문제 수), [currentProblem]: 그 칸의 무낙인 문제(완료 시 null).
 *  - [streak]: 백엔드가 read 경로에 실어 주면 채워진다(아직 미제공이면 null → Home은 스트릭을 숨긴다).
 */
data class DailySession(
    val sessionId: Long,
    val sessionDate: String,
    val status: SessionStatus,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val currentProblem: SessionProblem?,
    val streak: Streak? = null,
) {
    val isCompleted: Boolean get() = status == SessionStatus.COMPLETED
}

/**
 * 답 제출 결과.
 *  - [concepts]·[explanation]·[enrichment]·[representativeAnswer]: 정답(CORRECT)일 때만 채워진다(비정답은
 *    no-leak으로 null). 태깅 순서를 보존한 개념 목록 — 첫 번째가 대표 개념이다(문제가 개념 N—M으로 태깅될 수
 *    있다). [enrichment]는 '더 알아보기'(§5.7) — 없어도 되는 선택 콘텐츠다. [representativeAnswer]는 화면
 *    표시용 대표 정답 — 허용답을 나열하지 않고 이 값 하나만 확정 입력란에 보여준다([2026-07-16] 오너 결정).
 *  - [currentProblem]: 정답으로 전진한 뒤 지금 풀 무낙인 문제. 완료됐으면 null.
 *  - [streak]: 이 제출로 세션이 완료됐을 때만 채워진다(04 완료 화면이 쓴다).
 *  - [misconceptionHint]: 비정답이고 제출이 예상 오답(큐레이션됨)과 일치할 때만 채워진다(push·자동, 기능 2.5).
 *    없는 게 정상이다 — 큐레이션 안 된 오답은 일반 불일치로 흐른다(막다른 길 없음). 서버는 매칭 시 결과를
 *    MISMATCH로 확정한다(NEAR_MISS보다 우선). 무낙인: 있어도 오답 확정 아님, 정답 비노출.
 */
data class AttemptOutcome(
    val result: JudgeResult,
    val status: SessionStatus,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val concepts: List<String>?,
    val explanation: String?,
    val currentProblem: SessionProblem?,
    val streak: Streak?,
    val misconceptionHint: String? = null,
    val enrichment: Enrichment? = null,
    val representativeAnswer: String? = null,
) {
    val isCompleted: Boolean get() = status == SessionStatus.COMPLETED
}

/**
 * 정답 공개(안전판) 결과. 공개 후에도 [representativeAnswer]를 **직접 따라 입력**해야 다음으로 넘어간다.
 *  - [concepts]: 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
 *  - [representativeAnswer]: 화면 표시용 대표 정답 하나 — 허용답 나열은 화면에서 하지 않는다([2026-07-16] 오너
 *    결정). 도메인 불변식: `normalize(representativeAnswer)`가 허용답 집합에 포함된다(따라 입력 시 반드시 통과).
 *  - [enrichment]: '더 알아보기'(§5.7) — 정답 공개로 학습한 뒤도 정답 접근이 허용된 맥락이라 포함된다.
 */
data class Reveal(
    val concepts: List<String>,
    val explanation: String?,
    val representativeAnswer: String,
    val enrichment: Enrichment? = null,
    val category: String? = null,
)

/**
 * 지난 문제 다시 보기(읽기 전용). 이미 통과한 칸이므로 개념·모범답안을 공개해도 학습을 해치지 않는다.
 *  - [concepts]: 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
 *  - [representativeAnswer]: 화면 표시용 대표 정답 하나 — 허용답 나열은 화면에서 하지 않는다([2026-07-16] 오너 결정).
 *  - [enrichment]: '더 알아보기'(§5.7) — 정답 접근이 이미 허용된 맥락이라 포함된다.
 */
data class PastItem(
    val position: Int,
    val problemId: Long,
    val question: String,
    val codeSnippet: String?,
    val difficulty: String?,
    val submittedAnswer: String?,
    val result: JudgeResult,
    val revealed: Boolean,
    val concepts: List<String>,
    val explanation: String?,
    val representativeAnswer: String,
    val enrichment: Enrichment? = null,
    val category: String? = null,
)

/** 연속 학습 스트릭. 긍정 동기 전용 — 끊겨도 죄책감 연출 금지(UX 다크패턴 방지). */
data class Streak(
    val count: Int,
    val lastStudyDate: String?,
)

// ── 타입드 예외 ─────────────────────────────────────────────────────────────
// 서버는 세션 예외를 HTTP 상태 + 본문 errorCode로 알린다(SESSION_ALREADY_COMPLETED=409, ITEM_NOT_VIEWABLE=403).
// 저장소가 이를 도메인 예외로 번역해, 뷰모델이 시스템 오류와 구분해 친절히 처리한다.

/** 이미 완료된 세션에 제출·공개를 시도(서버 SESSION_ALREADY_COMPLETED). */
class SessionCompletedException : Exception("오늘 몫을 이미 마쳤어요")

/** 아직 도달하지 않은(또는 없는) 칸을 열람 시도(서버 ITEM_NOT_VIEWABLE). */
class ItemNotViewableException : Exception("아직 볼 수 없는 문제예요")
