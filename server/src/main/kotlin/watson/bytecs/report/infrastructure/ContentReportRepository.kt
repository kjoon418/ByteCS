package watson.bytecs.report.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import watson.bytecs.report.domain.ContentReport

interface ContentReportRepository : JpaRepository<ContentReport, Long> {

    /**
     * 계정 삭제 시 그 사용자의 신고를 지우지 않고 user_id만 null로 지워 익명화한다(D10).
     * 신고는 콘텐츠 품질 운영 데이터라 삭제 대상이 아니다 — 벌크 UPDATE로 영속성 컨텍스트를 거치지 않고 직접 반영한다.
     */
    @Modifying
    @Query("update ContentReport c set c.userId = null where c.userId = :userId")
    fun anonymizeByUserId(userId: Long): Int
}
