package watson.bytecs.session.application

import org.springframework.stereotype.Component
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import watson.bytecs.study.LearningHistory
import java.time.LocalDate
import kotlin.random.Random

/**
 * 오늘 세션의 '생성(INSERT)'(배정 선정 + 저장)만 책임지는 협력자.
 *
 * 배정 선정(복습 인터리빙·새 개념 무작위 셔플)이 서비스의 조회·응답 변환과 분리되도록 별도 빈으로 둔다.
 * 호출자([SessionService])의 쓰기 트랜잭션에 합류해(REQUIRED), 조회-없으면-생성이 한 트랜잭션에서 원자적으로 일어난다.
 * (유니크 제약을 제거한 D6·D9 이후, 저장 실패 격리를 위한 REQUIRES_NEW는 더 필요하지 않다 — 동시성은 '본인 한정 경합'뿐이다.)
 */
@Component
class SessionCreator(
    private val sessionRepository: SessionRepository,
    private val problemRepository: ProblemRepository,
    private val userRepository: UserRepository,
    private val reviewService: ReviewService,
    // 푼/배정 문제 풀은 [LearningHistory]로 조회한다(D6·D9 일원화 이후 세션 단독 출처).
    private val learningHistory: LearningHistory,
    // 선호 난이도 설정 시 새 개념 후보를 난이도 가중으로 정렬한다(미설정이면 사용하지 않는다).
    private val difficultyWeightedShuffler: DifficultyWeightedShuffler,
    // 새 개념 문제를 무작위로 뽑는 데 쓴다. 기본은 Random.Default이고, 테스트에서 시드 고정 Random을 주입해
    // 셔플 결과를 결정적으로 검증한다(운영에선 사용자마다·매일 다른 세션을 위해 매번 새 순서를 뽑는다).
    private val random: Random = Random.Default,
) {

    /**
     * 오늘 세션을 배정·저장한다(호출자의 쓰기 트랜잭션에 합류).
     * 배정 문제가 즉시 확정되도록 save로 영속화한다.
     */
    fun create(userId: Long, today: LocalDate): Session {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byId(userId) }
        val problemIds = assignProblemIds(user, today)

        return sessionRepository.save(Session.assign(userId, today, problemIds))
    }

    /**
     * 오늘 세션에 배정할 본 문제 id를 정한다. 복습(기능 3)을 새 개념 문제와 인터리빙한다.
     *  1. 복습 편입: 복습 시점이 도래한 개념의 복습 문제를 도래 순으로 먼저 채운다(상한 없음 — 세션 분량까지 채울 수 있다).
     *  2. 남은 칸: 새 개념 문제를 채운다 — 아직 안 푼 문제가 있으면 그중에서, 없으면 전체 풀 폴백(반복 허용).
     *     새 개념 후보는 매번 [random]으로 셔플해 무작위로 배정한다(모든 사용자가 같은 순서로 같은 문제를 받지 않게 — QA #6).
     * 중복 problemId는 선착순으로 제거하고(복습 우선), 세션 분량 초과분은 도래 순 우선으로 잘린다.
     * 정책: 복습은 도래 순으로 우선 배치하고, 남은 칸의 새 개념 문제는 무작위로 뽑는다.
     * (같은 학습 상태·같은 시드면 같은 세션이 나오지만, 운영의 Random.Default에선 매번 다른 순서가 나온다.)
     */
    private fun assignProblemIds(user: User, today: LocalDate): List<Long> {
        val size = user.settings.dailySessionSize
        // 서빙 게이트: 배정 후보 풀은 승인(APPROVED) 문제뿐이다. 복습 poolIds 가드도 이 풀을 공유하므로
        // 회수·비승인 문제는 재출제·유도형 예외에서도 자동으로 걸러진다(계획 §4.2).
        val allProblemIds = problemRepository.findApprovedIdsOrderByIdAsc()
        val poolIds = allProblemIds.toSet()

        // 1) 복습 편입: 도래 개념의 복습 문제(회수된 후보는 건너뜀). 배정 이력은 유도형 예외 판정에 쓴다.
        val assignedProblemIds = learningHistory.findAssignedProblemIds(user.id)
        val reviewProblemIds = reviewService.selectDueReviewProblemIds(user.id, today, assignedProblemIds, poolIds)

        // 2) 남은 칸을 채울 새 개념 후보: 아직 안 푼 문제 우선, 없으면 전체 풀 폴백.
        //    이미 푼 문제는 새 개념으로 다시 내지 않는다(같은 문제가 새 개념 배정으로 반복되지 않게).
        val solvedProblemIds = learningHistory.findSolvedProblemIds(user.id)
        val unseenProblemIds = allProblemIds.filter { it !in solvedProblemIds }
        val newConceptCandidates = selectNewConceptCandidates(user, allProblemIds, unseenProblemIds)

        // 복습 먼저 배치 → 남은 칸을 새 개념으로. LinkedHashSet으로 선착순 중복 제거·순서 보존, 분량에서 절단한다.
        val chosen = LinkedHashSet<Long>()
        for (problemId in reviewProblemIds) {
            if (chosen.size >= size) break
            chosen.add(problemId)
        }
        for (problemId in newConceptCandidates) {
            if (chosen.size >= size) break
            chosen.add(problemId)
        }

        if (chosen.isEmpty()) {
            // 콘텐츠 자체가 하나도 없어 어떤 세션도 만들 수 없는 경우.
            throw ProblemNotFoundException.noneAvailable()
        }
        return chosen.toList()
    }

    /**
     * 남은 칸을 채울 새 개념 후보의 순서를 정한다. 세 갈래다(계획 §3.2):
     *  1. 안 푼 문제가 없으면 → 전체 풀을 균등 무작위로(D8 반복 폴백). **난이도 가중을 적용하지 않는다**(정착 보호·콘텐츠 고갈 방지).
     *  2. 선호 난이도 미설정 → 안 푼 문제를 균등 무작위로(현행 경로 그대로, 회귀 0).
     *  3. 선호 난이도 설정 → 안 푼 문제를 거리 기반 가중 무작위로(완전 배제 없음·후보 있는 난이도에만 가중).
     * 어느 경우든 안 푼 후보에는 먼저 **연결 문제 하드 게이트**([filterUnlockedConnectedProblems], 계획 §3.2 · DI12)를
     * 적용해, 지정된 연결 문제 중 구성 개념을 모두 학습하지 못한 것을 걸러낸 뒤 순서화한다(**필터 → 가중** 순서 유지).
     * 게이트는 새 개념 후보에만 작용한다 — D8 반복 폴백(1)은 이미 푼 문제 재출제라 게이트 밖이고,
     * 복습 편입도 이 선정과 무관하다(호출부가 복습을 먼저 배치하고 남은 칸만 여기서 채운다 — 게이트·난이도 가중이 복습에 닿지 않는다).
     *
     * **게이트는 후보가 남는 한에서만 작용한다** — 게이트로 후보가 전부 빠지면(안 푼 문제가 전부 잠긴 연결 문제) 반복 폴백으로
     * 내려간다(막다른 길 없음, 세션 생성을 404로 깨뜨리지 않기 위한 최후 폴백. 신규 사용자+전량 연결 콘텐츠라는 병리적 구성에서는
     * 게이트가 완화되는 것을 수용). 이때도 D8 분기와 동일 취급이라 난이도 가중을 적용하지 않는다.
     */
    private fun selectNewConceptCandidates(
        user: User,
        allProblemIds: List<Long>,
        unseenProblemIds: List<Long>,
    ): List<Long> {
        if (unseenProblemIds.isEmpty()) {
            // D8 반복 폴백: 이미 푼 문제 재출제라 게이트·난이도 가중 모두 미적용. 기존 균등 셔플 그대로 둔다.
            return allProblemIds.shuffled(random)
        }
        // 연결 문제 하드 게이트: 순서화(균등·가중) 앞에서 먼저 후보를 좁힌다(필터 → 가중).
        val gatedUnseen = filterUnlockedConnectedProblems(user.id, unseenProblemIds)
        if (gatedUnseen.isEmpty()) {
            // 안 푼 후보가 전부 게이트에 걸려 비었다 → 막다른 길을 만들지 않도록 D8 반복 폴백과 동일하게 처리한다.
            return allProblemIds.shuffled(random)
        }
        val preferred = user.settings.preferredDifficulty
            ?: return gatedUnseen.shuffled(random)
        val difficultyByProblemId = problemRepository.findApprovedDifficultiesByIdIn(gatedUnseen)
            .associate { it.id to it.difficulty }
        return difficultyWeightedShuffler.order(gatedUnseen, difficultyByProblemId, preferred, random)
    }

    /**
     * 연결 문제 하드 게이트(계획 §3.2 · DI12). 새 개념 후보에서, **연결 문제로 지정된(integration=true)** 문제는
     * 그 문제의 **모든** 구성 개념에 대해 이 사용자의 학습 이력(숙련도 행)이 있을 때만 남긴다.
     * '학습했다'는 정답 처리로 숙련도 행이 존재한다는 뜻이다(레벨 무관 — 승급 임계 레벨≥1과 다른 기준).
     * **미지정 문제는 개념 수와 무관하게 그대로 통과한다** — 다개념이어도 플래그가 없으면 게이트 밖이다(DI12: 판별을 태깅 수에서 명시 속성으로 이관).
     * 입력 순서를 보존해 뒤이은 순서화(균등·가중)가 맡는다.
     *
     * 성능: 후보별 개별 조회(N+1) 대신, 보유 개념([ReviewService.findMasteredConceptIds])과
     * 지정된 연결 문제의 개념 매핑([ProblemRepository.findConceptIdsOfIntegrationProblems])을 각각 1회 조회해 메모리에서 접는다.
     * 매핑에 없는 문제(미지정)는 게이트 대상이 아니므로 통과시킨다.
     */
    private fun filterUnlockedConnectedProblems(userId: Long, unseenProblemIds: List<Long>): List<Long> {
        val conceptIdsByIntegrationProblem = problemRepository.findConceptIdsOfIntegrationProblems(unseenProblemIds)
            .groupBy({ it.problemId }, { it.conceptId })
        if (conceptIdsByIntegrationProblem.isEmpty()) {
            // 후보에 지정된 연결 문제가 하나도 없으면 게이트가 걸릴 것이 없다 — 보유 개념 조회도 아낀다.
            return unseenProblemIds
        }
        val masteredConceptIds = reviewService.findMasteredConceptIds(userId)
        return unseenProblemIds.filter { problemId ->
            val conceptIds = conceptIdsByIntegrationProblem[problemId]
            // 미지정 문제(매핑에 없음)는 통과. 지정된 연결 문제는 전 구성 개념을 학습했을 때만 통과.
            conceptIds == null || masteredConceptIds.containsAll(conceptIds)
        }
    }
}
