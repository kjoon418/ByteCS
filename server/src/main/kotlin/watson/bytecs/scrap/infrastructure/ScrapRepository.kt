package watson.bytecs.scrap.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.scrap.domain.Scrap

interface ScrapRepository : JpaRepository<Scrap, Long> {

    fun existsByUserIdAndProblemId(userId: Long, problemId: Long): Boolean

    fun findByUserIdAndProblemId(userId: Long, problemId: Long): Scrap?

    /** 본인 스크랩만 최신순으로. 사용자 격리는 이 userId 조건으로 보장한다. */
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Scrap>

    fun deleteByUserIdAndProblemId(userId: Long, problemId: Long)

    /** 계정 삭제 시 그 사용자의 스크랩을 일괄 삭제한다(학습 상태 삭제 흐름 편입). */
    fun deleteByUserId(userId: Long)
}
