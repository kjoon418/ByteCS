package watson.bytecs.interview.infrastructure

import org.springframework.stereotype.Component
import watson.bytecs.interview.domain.ExplanationJudge
import watson.bytecs.interview.domain.JudgeResult

/**
 * 결정적 Fake 채점기(계획 §4.2 — "포인트 문자열 포함 여부 매칭"). 실 AI 연동(C3) 전까지 전 프로파일에서 쓴다
 * ([review-todo]: C3가 실 API 클라이언트를 추가하면 이 빈을 프로파일로 가려야 한다 — local/test는 계속 Fake 유지).
 * 판정: 정규화한 설명이 정규화한 루브릭 포인트 문구를 부분 문자열로 포함하면 그 포인트는 충족이다.
 * 실패(null)를 흉내 내지 않는다 — Fake는 항상 성공해, 폴백 분기는 별도 테스트 더블로 검증한다.
 */
@Component
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
