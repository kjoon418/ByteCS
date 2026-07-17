package watson.bytecs.extrastudy.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.extrastudy.domain.ExtraStudy
import watson.bytecs.extrastudy.infrastructure.ExtraStudyRepository

/**
 * 추가 학습 행의 '생성(INSERT)'만 별도 트랜잭션으로 격리하는 협력자(세션 SessionCreator와 같은 관례).
 *
 * 사용자당 1행 유니크라 get-or-create가 경합할 수 있다. 저장 실패(유니크 제약 위반)의 롤백이 호출자 트랜잭션을 오염시키면
 * 이후 재조회가 성공해도 최종 커밋이 UnexpectedRollbackException으로 터진다(→ 500). 그래서 INSERT를 REQUIRES_NEW로 분리해
 * 실패 시 '이 새 트랜잭션만' 롤백되고 호출자 트랜잭션은 깨끗이 유지되게 한다(호출자가 잡아 재조회 가능).
 * 자기호출은 프록시를 타지 않으므로 반드시 별도 빈으로 분리해 주입받아 호출해야 REQUIRES_NEW가 적용된다.
 */
@Component
class ExtraStudyCreator(
    private val extraStudyRepository: ExtraStudyRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createInNewTransaction(userId: Long): ExtraStudy =
        extraStudyRepository.saveAndFlush(ExtraStudy.create(userId))
}
