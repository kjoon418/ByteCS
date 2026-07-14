package watson.bytecs.session

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
 * 세션에서 '지금 풀 문제'. 정답을 유추할 정보(개념·허용답·해설)는 담지 않는다(무낙인·비노출).
 */
data class SessionProblem(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
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
 *  - [concept]·[explanation]: 정답(CORRECT)일 때만 채워진다(비정답은 no-leak으로 null).
 *  - [currentProblem]: 정답으로 전진한 뒤 지금 풀 무낙인 문제. 완료됐으면 null.
 *  - [streak]: 이 제출로 세션이 완료됐을 때만 채워진다(04 완료 화면이 쓴다).
 */
data class AttemptOutcome(
    val result: JudgeResult,
    val status: SessionStatus,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val concept: String?,
    val explanation: String?,
    val currentProblem: SessionProblem?,
    val streak: Streak?,
) {
    val isCompleted: Boolean get() = status == SessionStatus.COMPLETED
}

/**
 * 정답 공개(안전판) 결과. 공개 후에도 모범답안 중 하나를 **직접 따라 입력**해야 다음으로 넘어간다.
 */
data class Reveal(
    val concept: String,
    val explanation: String?,
    val acceptableAnswers: List<String>,
)

/**
 * 지난 문제 다시 보기(읽기 전용). 이미 통과한 칸이므로 개념·모범답안을 공개해도 학습을 해치지 않는다.
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
    val concept: String,
    val explanation: String?,
    val acceptableAnswers: List<String>,
)

/** 연속 학습 스트릭. 긍정 동기 전용 — 끊겨도 죄책감 연출 금지(UX 다크패턴 방지). */
data class Streak(
    val count: Int,
    val lastStudyDate: String?,
)

// ── 타입드 예외 ─────────────────────────────────────────────────────────────
// 서버는 세션 예외를 HTTP 상태 + 본문 errorCode로 알린다(SESSION_ALREADY_COMPLETED·REVEAL_NOT_ALLOWED=409,
// ITEM_NOT_VIEWABLE=403). 저장소가 이를 도메인 예외로 번역해, 뷰모델이 시스템 오류와 구분해 친절히 처리한다.

/** 이미 완료된 세션에 제출·공개를 시도(서버 SESSION_ALREADY_COMPLETED). */
class SessionCompletedException : Exception("오늘 몫을 이미 마쳤어요")

/** 아직 오답을 내지 않아 정답 공개를 열 수 없음(서버 REVEAL_NOT_ALLOWED). */
class RevealNotAllowedException : Exception("아직 정답을 확인할 수 없어요")

/** 아직 도달하지 않은(또는 없는) 칸을 열람 시도(서버 ITEM_NOT_VIEWABLE). */
class ItemNotViewableException : Exception("아직 볼 수 없는 문제예요")
