package watson.bytecs.scrap.application

import org.springframework.stereotype.Component
import watson.bytecs.problem.application.dto.EnrichmentResponse
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
            enrichment = problem.enrichment?.let(EnrichmentResponse::from),
            representativeAnswer = problem.representativeAnswer,
            scrappedAt = scrap.createdAt,
        )
}
