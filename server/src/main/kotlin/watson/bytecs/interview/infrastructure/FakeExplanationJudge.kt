package watson.bytecs.interview.infrastructure

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import watson.bytecs.interview.domain.ExplanationJudge
import watson.bytecs.interview.domain.JudgeResult

/**
 * 결정적 Fake 채점기(계획 §4.2 — "포인트 문자열 포함 여부 매칭"). 무료·오프라인이 필요한 환경에서 쓴다:
 * 채점 공급자 미설정(`provider` 없음)이나 `fake`일 때 활성이며, `:server:test`·기본 프로파일이 여기 해당해 **실 API 호출 0**을 보장한다.
 * `provider=http`로 실 AI([HttpChatExplanationJudge])를 켜면 이 빈은 비활성이 되어 [ExplanationJudge] 후보가 하나로 유지된다.
 * 판정: 정규화한 설명이 정규화한 루브릭 포인트 문구를 부분 문자열로 포함하면 그 포인트는 충족이다.
 * 실패(null)를 흉내 내지 않는다 — Fake는 항상 성공해, 폴백 분기는 별도 테스트 더블로 검증한다.
 */
@Component
@ConditionalOnProperty(prefix = "bytecs.interview.judge", name = ["provider"], havingValue = "fake", matchIfMissing = true)
class FakeExplanationJudge : ExplanationJudge {

    override fun judge(rubricPoints: List<String>, explanation: String): JudgeResult {
        val normalizedExplanation = normalize(explanation)
        val satisfied = rubricPoints.map { point -> normalizedExplanation.contains(normalize(point)) }
        val satisfiedCount = satisfied.count { it }
        val comment = "짚은 포인트 ${satisfiedCount}개 / 전체 ${rubricPoints.size}개예요."
        return JudgeResult(satisfiedPoints = satisfied, comment = comment)
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(WHITESPACE_REGEX, "")

    private companion object {
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
