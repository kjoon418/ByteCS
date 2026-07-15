package watson.bytecs.scrap.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 사용자가 다시 보고 싶은 문제를 저장한 개인 북마크(사용자 소유·격리).
 * 공용 콘텐츠(Problem)와는 식별자(problemId)로만 느슨하게 연결한다 — 문제를 복제하지 않는 참조일 뿐이다.
 * 복습(간격 반복)과 독립이며(자동 재출제가 아니라 사용자 주도 열람), 계정 삭제 시 학습 상태의 일부로 함께 삭제된다.
 * 같은 사용자가 같은 문제를 두 번 스크랩하지 못하도록 (user_id, problem_id)에 유니크 제약을 둔다(멱등).
 */
@Entity
@Table(
    name = "scrap",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_scrap_user_problem", columnNames = ["user_id", "problem_id"]),
    ],
)
class Scrap(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
