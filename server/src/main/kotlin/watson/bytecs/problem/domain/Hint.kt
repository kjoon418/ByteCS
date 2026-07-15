package watson.bytecs.problem.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 문제에 큐레이션된 힌트 하나. [Problem] 애그리거트에 값으로 소속되며(@Embeddable), 약→강 순서는 소유 리스트의 순서(@OrderColumn)로 보장한다.
 * 종류(키워드/배경지식 등)를 타입으로 못박지 않는다 — 힌트 구성은 문제마다 다르다(0~N개, 고정 사다리 없음).
 * 본문은 정답을 노출하지 않는다(콘텐츠 신뢰성 가드레일). codeSnippet은 코드를 봐야 이해되는 문제에서만 채운다.
 */
@Embeddable
class Hint(
    @Column(name = "hint_text", nullable = false, columnDefinition = "text")
    val text: String,

    @Column(name = "hint_code_snippet", columnDefinition = "text")
    val codeSnippet: String? = null,
) {
    init {
        require(text.isNotBlank()) { "힌트 본문은 비어 있을 수 없습니다." }
    }
}
