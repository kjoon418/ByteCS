package watson.bytecs.scrap.application.dto

import watson.bytecs.problem.application.dto.EnrichmentResponse
import java.time.Instant

/**
 * 스크랩한 문제의 읽기 전용 재열람. 이미 스크랩(정답 접근 가능 맥락)한 것이므로 개념·모범답안·해설·심화 정보를 공개해도 학습 효과를 해치지 않는다.
 * 세션 진행 상태(위치·내가 쓴 답 등)와 무관하므로 PastItemResponse 대신 스크랩 전용 형태로 둔다.
 * representativeAnswer는 화면 표시용 대표 정답(모범답안). enrichment(심화 정보·'더 알아보기')는 문제에 없으면 null(graceful).
 */
data class ScrapDetailResponse(
    val problemId: Long,
    val question: String,
    val codeSnippet: String?,
    val difficulty: String?,
    val concepts: List<String>,
    val explanation: String?,
    val enrichment: EnrichmentResponse?,
    val representativeAnswer: String,
    val scrappedAt: Instant,
)
