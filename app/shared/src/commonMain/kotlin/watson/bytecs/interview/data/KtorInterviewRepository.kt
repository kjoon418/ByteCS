package watson.bytecs.interview.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import watson.bytecs.interview.ExplanationOutcome
import watson.bytecs.interview.InterviewHintReveal
import watson.bytecs.interview.InterviewRepository
import watson.bytecs.interview.InterviewResponseMappingException
import watson.bytecs.interview.InterviewSession
import watson.bytecs.interview.InterviewStatus
import watson.bytecs.interview.InterviewUnavailableException
import watson.bytecs.problem.data.platformApiBaseUrl
import kotlin.coroutines.cancellation.CancellationException

/**
 * 백엔드 면접 세션 REST API에 붙는 [InterviewRepository] 구현(C4-β). 인증 헤더는 공유 인증 클라이언트가
 * 요청마다 붙인다. 후보 산정·채점·상태 전이는 전적으로 서버에 위임한다.
 *
 * 오류 처리는 두 가지를 구분한다(session 슬라이스의 errorCode 기반 번역과 같은 관례):
 *  1. **홈으로 되돌릴 상태**(후보 없음·쿼터 소진·세션 없음/완료·회원 전용) — 홈 카드가 진입 전에 거르지만, 카드가
 *     낡았거나 세션이 도중에 끝나면 뒤늦게 도달한다. 본문 errorCode로 식별해 [InterviewUnavailableException]으로 올린다.
 *  2. **제출 응답 해석 실패**(2xx인데 본문 파싱·불변식 위반) — 서버는 이미 커서를 전진시켰으므로 재제출은 위험하다.
 *     전송 실패와 섞이지 않게 [InterviewResponseMappingException]으로 올린다.
 * 그 밖의 비-2xx·네트워크 오류는 원래 예외를 그대로 올려 뷰모델이 '전송 실패(재시도)'로 다루게 한다.
 */
class KtorInterviewRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : InterviewRepository {

    override suspend fun status(): InterviewStatus {
        val dto: InterviewStatusDto = client.get("$baseUrl/api/interview/status").body()
        return dto.toDomain()
    }

    override suspend fun startOrResumeToday(): InterviewSession =
        try {
            client.post("$baseUrl/api/interview/sessions/today").body<InterviewSessionDto>().toDomain()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: ResponseException) {
            // 진입 시점의 '오늘은 없음' 신호는 홈으로 되돌린다. 그 밖(로드 실패)은 그대로 올려 재시도 가능한 오류로 다룬다.
            throw unavailableOrNull(error) ?: error
        }

    override suspend fun submitExplanation(position: Int, text: String): ExplanationOutcome {
        val dto: InterviewAnswerResponseDto = try {
            client.post("$baseUrl/api/interview/sessions/today/answers") {
                contentType(ContentType.Application.Json)
                setBody(InterviewAnswerRequestDto(text))
            }.body()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: ResponseException) {
            // 세션이 이미 끝났거나(다른 기기 완료 등) 사라진 비-2xx는 홈으로, 그 밖은 전송 실패로 올린다.
            throw unavailableOrNull(error) ?: error
        } catch (deserialization: SerializationException) {
            // HTTP는 성공(2xx)했으나 본문을 역직렬화하지 못함 — 서버는 이미 답을 반영했으므로 재제출 금지.
            throw InterviewResponseMappingException(deserialization)
        }
        // 여기 도달 = 2xx + 역직렬화 성공. 도메인 변환의 불변식 위반도 '해석 실패'로 묶어 재제출을 막는다.
        return try {
            dto.toDomain()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (invariant: IllegalArgumentException) {
            throw InterviewResponseMappingException(invariant)
        } catch (invariant: IllegalStateException) {
            throw InterviewResponseMappingException(invariant)
        }
    }

    /**
     * 다음 힌트 하나를 연다. 열람 실패는 세션 진행을 막지 않는 무낙인 보조 장치이므로(디자인 08 §6-b), 여기서는
     * 오류를 특별히 번역하지 않고 그대로 올린다 — 뷰모델이 어떤 예외든 진행 표시만 내리고 조용히 흡수한다.
     */
    override suspend fun revealHint(revealedCount: Int): InterviewHintReveal {
        val dto: InterviewHintRevealResponseDto = client.post("$baseUrl/api/interview/sessions/today/hints/reveal") {
            contentType(ContentType.Application.Json)
            setBody(InterviewHintRevealRequestDto(revealedCount))
        }.body()
        return dto.toDomain()
    }

    /** 본문 errorCode가 '오늘은 진행할 세션 없음'류면 [InterviewUnavailableException]으로, 아니면 null(원래 예외 유지). */
    private suspend fun unavailableOrNull(error: ResponseException): InterviewUnavailableException? =
        when (errorCodeOf(error)) {
            "INTERVIEW_NO_CANDIDATE",
            "INTERVIEW_QUOTA_EXCEEDED",
            "INTERVIEW_SESSION_NOT_FOUND",
            "INTERVIEW_SESSION_ALREADY_COMPLETED",
            "INTERVIEW_MEMBER_ONLY",
            -> InterviewUnavailableException()

            else -> null
        }

    /** 오류 응답 본문에서 errorCode를 읽는다. 본문이 없거나 파싱 실패면 null(→ 시스템 오류로 처리). */
    private suspend fun errorCodeOf(error: ResponseException): String? =
        try {
            errorJson.decodeFromString(InterviewErrorBodyDto.serializer(), error.response.bodyAsText()).errorCode
        } catch (parseFailure: Throwable) {
            null
        }

    private companion object {
        // 서버 확장 필드에 견디도록 알 수 없는 키는 무시한다(오류 본문 파싱 전용).
        val errorJson = Json { ignoreUnknownKeys = true }
    }
}
