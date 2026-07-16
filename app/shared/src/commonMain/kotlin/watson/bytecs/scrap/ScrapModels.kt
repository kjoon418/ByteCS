package watson.bytecs.scrap

import watson.bytecs.problem.Enrichment

/**
 * 문제 스크랩(기능 5) 도메인 모델. 백엔드 `/api/scraps` 계약과 형태를 맞추되 DTO와 분리한다.
 */

/**
 * 스크랩 목록의 한 항목. 목록은 정답을 유추할 정보를 담지 않는다 — 문제 식별과 재열람 진입에 필요한 것만.
 * [question]이 null이면 스크랩한 문제가 회수·삭제된 것이다(재열람 불가 — '더 이상 볼 수 없음').
 */
data class ScrapListItem(
    val problemId: Long,
    val question: String?,
    val scrappedAt: String,
)

/**
 * 스크랩한 문제의 읽기 전용 재열람 내용. 이미 정답에 접근 가능한 맥락이므로 모범답안·해설을 공개해도 된다
 * (명세 [결정]: "문제·모범답안 등"). [representativeAnswer]는 화면 표시용 대표 정답 하나 —
 * 허용답 나열은 화면에서 하지 않는다([2026-07-16] 오너 결정).
 * [concepts]는 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
 * [enrichment]는 '더 알아보기'(§5.7) — 정답 접근이 이미 허용된 맥락이라 포함된다(없어도 되는 선택 콘텐츠).
 */
data class ScrapDetail(
    val problemId: Long,
    val question: String,
    val codeSnippet: String?,
    val concepts: List<String>,
    val explanation: String?,
    val representativeAnswer: String,
    val enrichment: Enrichment? = null,
)
