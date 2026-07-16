package watson.bytecs.scrap.application

import org.springframework.stereotype.Component
import watson.bytecs.problem.domain.Problem
import watson.bytecs.scrap.application.dto.ScrapDetailResponse
import watson.bytecs.scrap.application.dto.ScrapSummaryResponse
import watson.bytecs.scrap.domain.Scrap

/**
 * 스크랩 도메인·콘텐츠를 응답 DTO로 변환한다.
 * 스크랩 상세는 정답 접근이 이미 가능한 맥락이라 개념·모범답안·해설을 공개한다.
 */
@Component
class ScrapResponseMapper {

    /** 목록 항목. 문제가 회수·삭제됐으면 question은 null이다. */
    fun toSummaryResponse(scrap: Scrap, problem: Problem?): ScrapSummaryResponse =
        ScrapSummaryResponse(
            problemId = scrap.problemId,
            question = problem?.questionText,
            scrappedAt = scrap.createdAt,
        )

    fun toDetailResponse(scrap: Scrap, problem: Problem): ScrapDetailResponse =
        ScrapDetailResponse(
            problemId = problem.id,
            question = problem.questionText,
            codeSnippet = problem.codeSnippet,
            difficulty = problem.difficulty?.name,
            concepts = problem.conceptNames(),
            explanation = problem.explanation,
            acceptableAnswers = acceptableAnswers(problem),
            scrappedAt = scrap.createdAt,
        )

    /** 모범답안 목록을 결정적 순서로 돌려준다(가장 짧은 표기 먼저, 동률이면 사전순). */
    private fun acceptableAnswers(problem: Problem): List<String> =
        problem.acceptableAnswers.sortedWith(compareBy({ it.length }, { it }))
}
