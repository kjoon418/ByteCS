package watson.bytecs.scrap

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
 * (명세 [결정]: "문제·모범답안 등"). 여러 표현이 정답일 수 있어 [acceptableAnswers]는 목록이다.
 */
data class ScrapDetail(
    val problemId: Long,
    val question: String,
    val codeSnippet: String?,
    val concept: String,
    val explanation: String?,
    val acceptableAnswers: List<String>,
)
