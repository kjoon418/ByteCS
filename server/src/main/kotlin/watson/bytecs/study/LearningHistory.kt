package watson.bytecs.study

import org.springframework.stereotype.Component
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 사용자의 '푼 문제 풀'과 '배정 이력'을 제공하는 중립 협력자.
 *
 * 추가 학습이 세션으로 일원화된 뒤(D6·D9), 학습 이력의 출처는 세션 단독이다.
 * 세션 배정과 카테고리별 학습 이력 조회가 "무엇을 이미 만났고(assigned), 무엇을 이미 풀었는가(solved)"를
 * 같은 기준으로 보도록, 그 정의를 이 한곳에 응집한다 — 각 슬라이스가 세션 리포지토리를 직접 알 필요가 없다.
 *
 * (역사적 배경: 예전에는 세션 ∪ 추가 학습 합집합을 돌려주는 합류점이었다. 추가 학습 폐지 후 세션 단독으로 축소됐지만,
 *  '푼 문제 풀'의 정의를 한곳에 두는 응집점 역할은 그대로 유지한다.)
 */
@Component
class LearningHistory(
    private val sessionRepository: SessionRepository,
) {

    /**
     * 사용자가 '정답으로 통과한' 본 문제 id 집합.
     * 새 개념 배정에서 이미 푼 문제를 제외하는 기준이다.
     */
    fun findSolvedProblemIds(userId: Long): Set<Long> =
        sessionRepository.findSolvedProblemIds(userId).toSet()

    /**
     * 사용자가 '배정받은'(풀었든 아니든) 본 문제 id 집합.
     * 유도형 복습 예외의 '아직 만난 적 없는 다른 문제'를 가리는 기준이다.
     */
    fun findAssignedProblemIds(userId: Long): Set<Long> =
        sessionRepository.findAssignedProblemIds(userId).toSet()
}
