package watson.bytecs.interview.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import watson.bytecs.interview.ExplanationOutcome
import watson.bytecs.interview.InterviewRepository
import watson.bytecs.interview.InterviewSession
import watson.bytecs.interview.InterviewStatus
import watson.bytecs.problem.data.platformApiBaseUrl

/**
 * 백엔드 면접 세션 REST API에 붙는 [InterviewRepository] 구현(C4-β). 인증 헤더는 공유 인증 클라이언트가
 * 요청마다 붙인다. 후보 산정·채점·상태 전이는 전적으로 서버에 위임한다.
 *
 * 회원 전용(게스트 403)·후보 없음(404)·쿼터 소진(409) 같은 도메인 예외는 홈 카드([InterviewStatus])가
 * 진입 전에 이미 걸러 준다는 전제라 별도 타입 번역 없이 그대로 올린다(session 슬라이스의 mapSessionErrors와
 * 달리, 뷰모델이 이 코드들을 구분해 다르게 그리지 않는다 — 전부 시스템 오류/카드 숨김으로 수렴).
 */
class KtorInterviewRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : InterviewRepository {

    override suspend fun status(): InterviewStatus {
        val dto: InterviewStatusDto = client.get("$baseUrl/api/interview/status").body()
        return dto.toDomain()
    }

    override suspend fun startOrResumeToday(): InterviewSession {
        val dto: InterviewSessionDto = client.post("$baseUrl/api/interview/sessions/today").body()
        return dto.toDomain()
    }

    override suspend fun submitExplanation(position: Int, text: String): ExplanationOutcome {
        val dto: InterviewAnswerResponseDto = client.post("$baseUrl/api/interview/sessions/today/answers") {
            contentType(ContentType.Application.Json)
            setBody(InterviewAnswerRequestDto(text))
        }.body()
        return dto.toDomain()
    }
}
