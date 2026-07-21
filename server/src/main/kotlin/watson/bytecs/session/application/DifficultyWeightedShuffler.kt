package watson.bytecs.session.application

import org.springframework.stereotype.Component
import watson.bytecs.problem.domain.Difficulty
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * 선호 난이도를 기준으로 새 개념 후보를 '난이도 가중 무작위'로 정렬한다(계획 §3.2).
 *
 * 규칙 자체는 결정적이고(거리 기반 6:3:1 가중치) 추첨만 무작위다 — 균등 셔플을 가중 셔플로 바꾸는 것뿐이다.
 * 가중은 **완전 배제가 없다**: 가장 먼 난이도(거리 2)·난이도 미상도 최소 가중치 1을 받아 소량은 계속 나온다
 * (편식·콘텐츠 고갈 방지). '후보가 존재하는 난이도에만 가중'은 자연히 성립한다 — 후보 목록에 없는 난이도는
 * 애초에 정렬 대상이 아니므로 가중치가 실릴 곳이 없다(풀이 빈 난이도는 자연 제외).
 *
 * 정렬 방식은 Efraimidis–Spirakis 가중 무작위 추출이다: 후보 i에 key = u_i^(1/w_i)(u_i~U(0,1))를 부여하고
 * key 내림차순으로 정렬한다. 가중치가 클수록 key가 1에 가까워져 앞에 올 확률이 높지만, 어떤 후보의 확률도 0이 아니다.
 * 같은 후보·같은 시드([random])면 같은 순서가 나온다(SessionCreator의 결정적 검증과 정합).
 */
@Component
class DifficultyWeightedShuffler {

    /**
     * 후보를 난이도 가중 무작위 순서로 돌려준다. 모든 후보가 정확히 한 번씩 포함된다(완전 배제 없음).
     * [difficultyByProblemId]에 없는(또는 난이도 미상인) 후보는 가장 먼 거리로 취급해 최소 가중치를 준다.
     */
    fun order(
        candidates: List<Long>,
        difficultyByProblemId: Map<Long, Difficulty?>,
        preferred: Difficulty,
        random: Random,
    ): List<Long> =
        candidates
            .map { problemId ->
                val weight = weightOf(difficultyByProblemId[problemId], preferred)
                // u^(1/w): 가중치가 클수록 key가 1에 가까워져 앞에 올 확률이 커진다. w>=1이라 key>0 → 항상 포함된다.
                problemId to random.nextDouble().pow(1.0 / weight)
            }
            .sortedByDescending { (_, key) -> key }
            .map { (problemId, _) -> problemId }

    /**
     * 선호 난이도와의 거리(|ordinal 차|)로 가중치를 정한다: 거리 0→6, 1→3, 2→1.
     * 난이도 미상(null)은 가장 먼 거리로 보아 최소 가중치를 준다(완전 배제 없음 유지).
     */
    fun weightOf(difficulty: Difficulty?, preferred: Difficulty): Int {
        val distance = difficulty?.let { abs(it.ordinal - preferred.ordinal) } ?: FARTHEST_DISTANCE
        return when (distance) {
            0 -> WEIGHT_NEAR
            1 -> WEIGHT_MID
            else -> WEIGHT_FAR
        }
    }

    companion object {
        // 거리 기반 가중치(6:3:1). 운영 튜닝 대상이라 상수로 분리한다(계획 §3.2 — 명세엔 원칙만, 수치는 운영 판단).
        const val WEIGHT_NEAR = 6
        const val WEIGHT_MID = 3
        const val WEIGHT_FAR = 1

        // Difficulty 3단계에서 가능한 최대 거리(EASY↔HARD). 난이도 미상도 이 거리로 취급한다.
        private const val FARTHEST_DISTANCE = 2
    }
}
