package watson.bytecs.extrastudy.data

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
import watson.bytecs.extrastudy.ExtraStudyAttempt
import watson.bytecs.extrastudy.ExtraStudyHintReveal
import watson.bytecs.extrastudy.ExtraStudyNoOpenItemException
import watson.bytecs.extrastudy.ExtraStudyRepository
import watson.bytecs.extrastudy.ExtraStudyReveal
import watson.bytecs.extrastudy.ExtraStudyState
import watson.bytecs.problem.data.platformApiBaseUrl

/**
 * 백엔드 추가 학습 REST API에 붙는 [ExtraStudyRepository] 구현. 인증 헤더는 공유 인증 클라이언트가 요청마다 붙인다.
 * 선정·판정·완료는 전적으로 서버에 위임한다(클라이언트는 상태를 그리기만 한다).
 *
 * ⭐️ 추가 학습 특유의 경합(열린 항목 없음)은 본문 [ExtraStudyErrorBodyDto.errorCode]로 구별해 타입드 예외로
 * 번역한다(세션 `mapSessionErrors` 관례). 그 외 비-2xx·네트워크 오류는 그대로 올려 뷰모델이 시스템 오류로
 * 처리한다(무낙인·"학습 기록은 안전해요"). 소진은 오류가 아니라 정상 GET 상태라 여기서 다루지 않는다.
 */
class KtorExtraStudyRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : ExtraStudyRepository {

    override suspend fun getCurrent(): ExtraStudyState {
        val dto: ExtraStudyCurrentResponseDto = client.get("$baseUrl/api/extra-study/current").body()
        return dto.toDomain()
    }

    override suspend fun submitAttempt(answer: String): ExtraStudyAttempt = mapExtraStudyErrors {
        val dto: ExtraStudyAttemptResponseDto = client.post("$baseUrl/api/extra-study/attempts") {
            contentType(ContentType.Application.Json)
            setBody(ExtraStudyAttemptRequestDto(answer))
        }.body()
        dto.toDomain()
    }

    override suspend fun reveal(): ExtraStudyReveal = mapExtraStudyErrors {
        val dto: ExtraStudyRevealResponseDto = client.post("$baseUrl/api/extra-study/reveal").body()
        dto.toDomain()
    }

    override suspend fun revealHint(revealedCount: Int): ExtraStudyHintReveal = mapExtraStudyErrors {
        val dto: ExtraStudyHintRevealResponseDto = client.post("$baseUrl/api/extra-study/hints/reveal") {
            contentType(ContentType.Application.Json)
            setBody(ExtraStudyHintRevealRequestDto(revealedCount))
        }.body()
        dto.toDomain()
    }

    /**
     * 추가 학습 API 호출의 비-2xx 응답을 도메인 예외로 번역한다. 본문 errorCode가 열린 항목 경합이면 타입드
     * 예외로, 그 밖은 원래 [ResponseException]을 그대로 던져 시스템 오류로 다루게 한다.
     */
    private suspend fun <T> mapExtraStudyErrors(block: suspend () -> T): T =
        try {
            block()
        } catch (error: ResponseException) {
            when (errorCodeOf(error)) {
                "EXTRA_STUDY_NO_OPEN_ITEM" -> throw ExtraStudyNoOpenItemException()
                else -> throw error
            }
        }

    /** 오류 응답 본문에서 errorCode를 읽는다. 본문이 없거나 파싱 실패면 null(→ 시스템 오류로 처리). */
    private suspend fun errorCodeOf(error: ResponseException): String? =
        try {
            errorJson.decodeFromString(ExtraStudyErrorBodyDto.serializer(), error.response.bodyAsText()).errorCode
        } catch (parseFailure: Throwable) {
            null
        }

    private companion object {
        // 서버 확장 필드에 견디도록 알 수 없는 키는 무시한다(오류 본문 파싱 전용).
        val errorJson = Json { ignoreUnknownKeys = true }
    }
}
