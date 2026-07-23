package watson.bytecs.interview

import watson.bytecs.session.Streak

/**
 * 면접 세션(면접 대비 가치 계획 §3.3·§3.4) 슬라이스의 도메인 모델. 서버 `/api/interview` 계약과 형태를
 * 맞추되(C4-β에서 Ktor 연동), UI·뷰모델은 이 모델만 본다(유선 DTO와 분리).
 *
 * 도메인 전제(계획 §3.3): 회원 전용 · 하루 1세션 · 세션 기본 3문제 · AI 루브릭 채점(무낙인).
 * 채점은 결정성이 없는 유일한 지점이므로, 그 산출물([InterviewReadiness])은 숙련도 레벨에 반영되지 않는다(DI4).
 */

/** 세션 진행 상태. 분량(면접 질문)을 모두 제출하면 IN_PROGRESS→COMPLETED로 단방향 전이한다. */
enum class InterviewSessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ;

    companion object {
        /** 서버 문자열을 대소문자 무시로 매핑. 미지값은 진행 중으로 보수적 처리(막다른 길 방지). */
        fun from(raw: String): InterviewSessionStatus =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: IN_PROGRESS
    }
}

/**
 * 홈(02 3-b) 면접 연습 진입 카드의 단일 출처(계획 §4.2 `GET /api/interview/status`). 클라이언트는 이 값으로
 * 카드 상태를 그릴 뿐 재판단하지 않는다.
 *  - [guest]: 게스트 여부(회원 전용이라 게스트는 가입 유도로 대체).
 *  - [candidateCount]: 현재 승급된(면접 세션 후보) 개념 수 — 게스트에게도 계산해 가입 유도 문구용으로 준다.
 *  - [remainingToday]: 오늘 남은 세션 쿼터(기본 하루 1). 0이면 오늘 소진.
 */
data class InterviewStatus(
    val guest: Boolean,
    val candidateCount: Int,
    val remainingToday: Int,
)

/**
 * 면접 세션의 한 문항(면접 질문). 정답을 유추할 정보(모범 설명·루브릭)는 담지 않는다 — 제출 후 채점 결과로만 받는다.
 *  - [position]: 세션 내 위치(0-based). 표시용 번호는 position+1.
 *  - [conceptName]: 이 문항이 검증하는 개념명(질문의 맥락).
 *  - [question]: 질문 문구("○○을 설명해보세요" 류 — 정의 재생형 단답과 문형이 다르다).
 *  - [promptId]: 면접 질문 콘텐츠 식별자 — 오류 신고 대상(질문·모범 설명·루브릭).
 */
data class InterviewItem(
    val position: Int,
    val conceptName: String,
    val question: String,
    val promptId: Long,
)

/**
 * 오늘의 면접 세션 상태(진입·재개). 중단·재개는 일반 세션과 동일하게 서버에 영속된다(계획 §4.2).
 *  - [position]: 지금 답할 칸(0-based), [currentItem]: 그 칸의 문항(완료 시 null).
 */
data class InterviewSession(
    val sessionId: Long,
    val sessionDate: String,
    val status: InterviewSessionStatus,
    val position: Int,
    val totalCount: Int,
    val currentItem: InterviewItem?,
) {
    val isCompleted: Boolean get() = status == InterviewSessionStatus.COMPLETED
}

/** 사용자·개념별 면접 준비도(설명 검증 상태, 계획 §3.3). 숙련도와 독립된 축(DI4). */
enum class InterviewReadiness {
    /** 미검증 — 아직 설명을 검증하지 않음. */
    UNVERIFIED,

    /** 부분 — 일부 루브릭 포인트만 충족. */
    PARTIAL,

    /** 검증됨 — 전 포인트 충족. */
    VERIFIED,
}

/** 루브릭 포인트 하나의 채점 결과. [satisfied]=이 포인트를 사용자의 설명이 짚었는지(포인트별 충족 여부만 판정). */
data class RubricPoint(
    val text: String,
    val satisfied: Boolean,
)

/**
 * AI 루브릭 채점 결과(계획 §3.3 AI 채점 원칙).
 *  - [points]: 포인트별 충족 여부(순서 보존). 폴백이면 비어 있다.
 *  - [comment]: AI 한 줄 코멘트(선택 — 없을 수 있다).
 *  - [fallback]: 채점 실패(타임아웃·오류, 재시도 후) — 체크리스트 없이 모범 설명만 공개하고 준비도 미갱신.
 *  - [readiness]: 이 채점으로 파생된 준비도(폴백이면 미갱신이라 화면은 성공일 때만 읽는다).
 *
 * ⭐️ 무낙인: 점수·합불이 아니라 "짚은 포인트 / 보완하면 좋은 포인트"로만 표현한다(화면 책임).
 */
data class ExplanationJudgeResult(
    val points: List<RubricPoint>,
    val comment: String?,
    val fallback: Boolean,
    val readiness: InterviewReadiness,
) {
    /** 짚은(충족) 포인트 수. */
    val satisfiedCount: Int get() = points.count { it.satisfied }

    /** 보완하면 좋은(미충족) 포인트 수. */
    val unsatisfiedCount: Int get() = points.count { !it.satisfied }

    /** '검증됨'(전 포인트 충족). 폴백이면 검증되지 않은 것으로 본다(준비도 미갱신). */
    val verified: Boolean get() = !fallback && readiness == InterviewReadiness.VERIFIED
}

/**
 * 면접 세션 완료 요약(마지막 문항 제출 응답에만 실린다 — 계획 §4.2, 디자인 08 §11-b).
 *  - [practicedConceptCount]: "오늘 개념 N개를 면접처럼 설명해봤어요"의 N(이 세션에서 채점이 진행된 문항 수).
 *  - [streak]: **이번 완료로 스트릭이 실제로 기록된 경우에만** 채워진다(DI5 하루 멱등 — 그날 이미 다른 완료로
 *    채워져 있었다면 null이라 완료 화면이 스트릭 줄 자체를 그리지 않는다). 중복 축하 금지.
 */
data class InterviewCompletion(
    val practicedConceptCount: Int,
    val streak: Streak?,
)

/**
 * 설명 제출·채점 결과(계획 §4.2 `POST /api/interview/sessions/today/answers`).
 *  - [result]: 채점 결과(성공 체크리스트 또는 폴백).
 *  - [modelAnswer]: 모범 설명 — 성공·폴백 모두 공개한다.
 *  - [conceptName]: 이 문항의 개념명(결과 문맥).
 *  - [reviewProblemId]: '그때 푼 문제 다시 보기'(DI10) 대상 — '검증됨' 미달이고 재열람할 문제가 있을 때만.
 *    없으면(null) 링크를 그리지 않는다.
 *  - [status]·[nextItem]: 서버 커서. 완료면 nextItem=null·status=COMPLETED.
 *  - [completion]: 이 제출로 세션이 완료됐을 때만 채워진다(완료 요약 블록의 데이터 소스).
 */
data class ExplanationOutcome(
    val result: ExplanationJudgeResult,
    val modelAnswer: String,
    val conceptName: String,
    val reviewProblemId: Long?,
    val status: InterviewSessionStatus,
    val nextItem: InterviewItem?,
    val completion: InterviewCompletion?,
) {
    val isCompleted: Boolean get() = status == InterviewSessionStatus.COMPLETED
}
