package watson.bytecs.categoryhistory

import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.JudgeResult

/**
 * 카테고리별 학습 이력(도메인 명세 §7 '카테고리별 학습 이력', 1차) 도메인 모델. 백엔드
 * `/api/learning-history/categories` 계약과 형태를 맞추되 DTO(유선 형태)와 분리한다.
 *
 * ⭐️ 세션 ∪ 추가 학습에서 **정답으로 통과한** 문제만 담는 읽기 전용 이력이다(복습과 독립, 스크랩과 유사한
 * 재열람 성격 — 자동 재출제 아님). 이미 정답에 접근 가능한 맥락이므로 모범답안·개념·해설을 공개한다.
 */

/**
 * 카테고리별 이력의 한 항목.
 *  - [submittedAnswer]: 세션 출처만 보존된다 — 추가 학습에서만 푼 문제는 null이 정상(서버 [결정], 도메인
 *    구조상 열린 항목이 승격되며 제출 답을 남기지 않는다). null이어도 나머지 정보는 그대로 제공된다.
 *  - [result]: '정답으로 통과한' 문제만 담으므로 항상 CORRECT다(PastItem·ScrapDetail과 같은 규약).
 *  - [concepts]: 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
 */
data class CategoryHistoryItem(
    val problemId: Long,
    val question: String,
    val codeSnippet: String?,
    val difficulty: String?,
    val submittedAnswer: String?,
    val result: JudgeResult,
    val concepts: List<String>,
    val explanation: String?,
    val representativeAnswer: String,
    val enrichment: Enrichment? = null,
)

/**
 * 한 카테고리의 그룹. [category]는 서버 [watson.bytecs.problem.domain.ProblemCategory] enum name(예:
 * "DATA_STRUCTURE") — 화면은 `categoryLabel`로 한글 라벨을 얻는다.
 *
 * 서버는 8개 고정 대분류를 **항상 전부** 반환한다(문제 없는 카테고리는 [items]가 빈 목록) — 클라는 빈
 * 목록을 오류가 아니라 '준비 중'(긍정 빈 상태, UX 가이드 9)으로 렌더한다.
 */
data class CategoryHistoryGroup(
    val category: String,
    val items: List<CategoryHistoryItem>,
)
