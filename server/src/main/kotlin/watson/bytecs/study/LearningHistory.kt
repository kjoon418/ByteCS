package watson.bytecs.study

import org.springframework.stereotype.Component
import watson.bytecs.extrastudy.infrastructure.ExtraStudyRepository
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 사용자의 '푼 문제 풀'을 세션과 추가 학습에 걸쳐 하나로 합쳐 제공하는 중립 협력자.
 *
 * 세션 배정과 추가 학습 선정은 "무엇을 이미 만났고(assigned), 무엇을 이미 풀었는가(solved)"를 같은 기준으로
 * 봐야 "추가 학습에서 푼 문제 = 세션에서 푼 문제"라는 학습 데이터 의미론 통일이 성립한다(설계 §0·§1.2).
 * 그래서 두 활동의 이력을 합집합으로 돌려주는 지점을 한곳에 응집한다 — 각 슬라이스가 상대의 리포지토리를 직접 알 필요가 없다.
 *
 * 의존은 두 슬라이스의 infra(리포지토리)로만 향하고, 두 슬라이스의 application이 이 컴포넌트에 의존한다(순환 없음).
 */
@Component
class LearningHistory(
    private val sessionRepository: SessionRepository,
    private val extraStudyRepository: ExtraStudyRepository,
) {

    /**
     * 사용자가 어디서든 '정답으로 통과한' 본 문제 id 집합(세션 ∪ 추가 학습).
     * 새 개념 배정·추가 학습 선정에서 이미 푼 문제를 제외하는 기준이다.
     */
    fun findSolvedProblemIds(userId: Long): Set<Long> =
        (sessionRepository.findSolvedProblemIds(userId) + extraStudyRepository.findSolvedProblemIds(userId)).toSet()

    /**
     * 사용자가 어디서든 '배정/제시받은'(풀었든 아니든) 본 문제 id 집합(세션 ∪ 추가 학습).
     * 유도형 복습 예외의 '아직 만난 적 없는 다른 문제'를 가리는 기준이다.
     * 추가 학습은 solved에 더해 지금 열린(이어 풀) 문제도 '이미 만난 문제'이므로 배정 이력에 편입한다.
     */
    fun findAssignedProblemIds(userId: Long): Set<Long> {
        val assigned = (sessionRepository.findAssignedProblemIds(userId) +
            extraStudyRepository.findSolvedProblemIds(userId)).toMutableSet()
        extraStudyRepository.findOpenProblemId(userId)?.let { assigned.add(it) }
        return assigned
    }
}
