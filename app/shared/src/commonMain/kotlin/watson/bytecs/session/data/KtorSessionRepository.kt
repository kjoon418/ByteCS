package watson.bytecs.session.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import watson.bytecs.problem.data.platformApiBaseUrl
import watson.bytecs.session.AttemptOutcome
import watson.bytecs.session.DailySession
import watson.bytecs.session.HintReveal
import watson.bytecs.session.ItemNotViewableException
import watson.bytecs.session.PastItem
import watson.bytecs.session.Reveal
import watson.bytecs.session.RevealNotAllowedException
import watson.bytecs.session.SessionCompletedException
import watson.bytecs.session.SessionRepository

/**
 * 백엔드 세션 REST API에 붙는 [SessionRepository] 구현. 인증 헤더는 공유 인증 클라이언트가 요청마다 붙인다.
 * 판정·진행·완료·스트릭은 전적으로 서버에 위임한다(클라이언트는 상태를 그리기만 한다).
 *
 * ⭐️ 세션 특유의 충돌은 상태 코드가 아니라 본문 [ErrorBodyDto.errorCode]로 구별해 도메인 예외로 번역한다
 * (ITEM_NOT_VIEWABLE=403, 나머지=409라 상태만으론 안 갈림). 그 외 비-2xx·네트워크 오류는 그대로 올려
 * 뷰모델이 시스템 오류로 처리한다(무낙인·"학습 기록은 안전해요").
 */
class KtorSessionRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : SessionRepository {

    override suspend fun getToday(): DailySession {
        val dto: SessionStateDto = client.get("$baseUrl/api/sessions/today").body()
        return dto.toDomain()
    }

    override suspend fun submitAttempt(answer: String): AttemptOutcome = mapSessionErrors {
        val dto: SessionAttemptResponseDto = client.post("$baseUrl/api/sessions/today/attempts") {
            contentType(ContentType.Application.Json)
            setBody(SessionAttemptRequestDto(answer))
        }.body()
        dto.toDomain()
    }

    override suspend fun reveal(): Reveal = mapSessionErrors {
        val dto: RevealResponseDto = client.post("$baseUrl/api/sessions/today/reveal").body()
        dto.toDomain()
    }

    override suspend fun revealHint(revealedCount: Int): HintReveal = mapSessionErrors {
        val dto: HintStateResponseDto = client.post("$baseUrl/api/sessions/today/hints/reveal") {
            contentType(ContentType.Application.Json)
            setBody(HintRevealRequestDto(revealedCount))
        }.body()
        dto.toDomain()
    }

    override suspend fun getPastItem(position: Int): PastItem = mapSessionErrors {
        val dto: PastItemResponseDto = client.get("$baseUrl/api/sessions/today/items/$position").body()
        dto.toDomain()
    }

    /**
     * 세션 API 호출의 비-2xx 응답을 도메인 예외로 번역한다. 본문 errorCode가 세션 충돌이면 타입드 예외로,
     * 그 밖(읽기 실패 포함)은 원래 [ResponseException]을 그대로 던져 시스템 오류로 다루게 한다.
     */
    private suspend fun <T> mapSessionErrors(block: suspend () -> T): T =
        try {
            block()
        } catch (error: ResponseException) {
            when (errorCodeOf(error)) {
                "SESSION_ALREADY_COMPLETED" -> throw SessionCompletedException()
                "REVEAL_NOT_ALLOWED" -> throw RevealNotAllowedException()
                "ITEM_NOT_VIEWABLE" -> throw ItemNotViewableException()
                else -> throw error
            }
        }

    /** 오류 응답 본문에서 errorCode를 읽는다. 본문이 없거나 파싱 실패면 null(→ 시스템 오류로 처리). */
    private suspend fun errorCodeOf(error: ResponseException): String? =
        try {
            errorJson.decodeFromString(ErrorBodyDto.serializer(), error.response.bodyAsText()).errorCode
        } catch (parseFailure: Throwable) {
            null
        }

    private companion object {
        // 서버 확장 필드에 견디도록 알 수 없는 키는 무시한다(오류 본문 파싱 전용).
        val errorJson = Json { ignoreUnknownKeys = true }
    }
}
