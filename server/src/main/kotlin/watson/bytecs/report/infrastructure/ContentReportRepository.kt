package watson.bytecs.report.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import watson.bytecs.report.domain.ContentReport

interface ContentReportRepository : JpaRepository<ContentReport, Long> {

    /**
     * 계정 삭제 시 그 사용자의 신고를 지우지 않고 user_id만 null로 지워 익명화한다(D10).
     * 신고는 콘텐츠 품질 운영 데이터라 삭제 대상이 아니다 — 벌크 UPDATE로 영속성 컨텍스트를 거치지 않고 직접 반영한다.
     *
     * 전제(m2): 벌크 UPDATE는 영속성 컨텍스트(1차 캐시)를 우회하므로, 이 호출이 일어나는 deleteMe 트랜잭션에
     * 관리 중(managed)인 ContentReport 엔티티가 없다는 전제에 의존한다(현재 삭제 경로는 신고를 조회하지 않는다).
     * 만약 삭제 경로에 신고 조회가 추가되면 캐시의 스테일 엔티티와 어긋날 수 있으므로,
     * 그때는 @Modifying(clearAutomatically = true)로 컨텍스트를 비울지 재검토한다.
     */
    @Modifying
    @Query("update ContentReport c set c.userId = null where c.userId = :userId")
    fun anonymizeByUserId(userId: Long): Int
}
