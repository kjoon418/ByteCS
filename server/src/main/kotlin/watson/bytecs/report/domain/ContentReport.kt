package watson.bytecs.report.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 서빙된 콘텐츠(문제)에 대한 사용자 오류 신고. 게이트를 뚫은 오류의 안전망이자 지속적 품질 장치다.
 * 공용 콘텐츠(Problem)와는 식별자(problemId)로만 느슨하게 연결한다(신고자의 학습 상태 귀속, 콘텐츠는 공용).
 * 유형([category])은 필수 단일 선택, 상세 내용([message])은 선택(없으면 null)이다.
 * 큐레이터의 수정·회수는 운영 영역이라 MVP API 범위 밖이므로, 이 엔티티는 접수만 기록한다.
 */
@Entity
@Table(name = "content_report")
class ContentReport(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    val category: ReportCategory,

    @Column(name = "message", columnDefinition = "text")
    val message: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
