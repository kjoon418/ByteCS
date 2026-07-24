package watson.bytecs.problem.infrastructure

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Enrichment
import watson.bytecs.problem.domain.EnrichmentItem
import watson.bytecs.problem.domain.InvalidApprovalStateException
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemCategory
import watson.bytecs.problem.domain.ProblemType

/**
 * 시드 JSON(`problems-generated.json`)이 로더를 거쳐 의도대로 조립·판정되는지 검증한다.
 * 허용답 집합과 유형 태깅은 시드에만 존재하는 데이터라, 도메인 단위 테스트로는 잡히지 않는다.
 *
 * 로더는 곧 검증기다 — 잘못된 시드 데이터(필수 필드 누락·불변식 위반)는 조용히 스킵되지 않고
 * 예외로 기동을 실패시켜야 한다([잘못된_시드_데이터는_조용히_스킵되지_않는다]).
 */
class ProblemDataLoaderTest {

    private val conceptRepository = mock(ConceptRepository::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val problemDataLoader = ProblemDataLoader(conceptRepository, problemRepository, jacksonObjectMapper())

    /**
     * 로더의 조립·판정 동작을 검증하는 앵커 픽스처(`loader-fixture.json`).
     * 운영/테스터용 메인 시드([problemDataLoader])는 흥미 카테고리 위주로 큐레이션되어 알고리즘·자료구조 문제가 없다.
     * 그래서 유도형 근접 억제(시간 복잡도)·정의 재생형 근접(해시 충돌)·한 글자 허용답(큐)·복수 개념 공유(스레드+스택) 같은
     * 대표 동작은, 콘텐츠 큐레이션에 흔들리지 않도록 이 전용 픽스처에 고정해 검증한다.
     * 반대로 no-leak·대표 정답 불변식·승인 상태처럼 "실제 시드 콘텐츠가 요건을 지키는가"는 메인 시드로 전수 검증한다.
     */
    private val fixtureLoader = ProblemDataLoader(
        conceptRepository,
        problemRepository,
        jacksonObjectMapper(),
        resourcePath = "seed/loader-fixture.json",
    )

    private companion object {
        const val TIME_COMPLEXITY = "시간 복잡도"
        const val HASH_COLLISION = "해시 충돌"
        const val QUEUE = "큐"
        const val STACK = "스택"
        const val PROCESS_AND_THREAD = "프로세스와 스레드"
    }

    @Nested
    inner class 시간_복잡도_문제는_유도형이다 {

        @Test
        fun 유형이_유도형으로_태깅된다() {
            val timeComplexityProblem = fixtureProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.type).isEqualTo(ProblemType.DERIVATION)
        }

        @Test
        fun 제곱_기호만_쓴_표기도_정답이다() {
            val timeComplexityProblem = fixtureProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.judge(AnswerText("n²"))).isEqualTo(Judgement.CORRECT)
        }

        @Test
        fun 유도를_틀린_답은_근접이_아니라_불일치다() {
            // 이중 반복문을 하나로 잘못 센 전형적인 오답. 편집거리는 1이지만 오타가 아니다.
            val timeComplexityProblem = fixtureProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.judge(AnswerText("o(n)"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Nested
    inner class 개념_이름을_묻는_문제는_정의_재생형이다 {

        @Test
        fun 유형이_정의_재생형으로_태깅된다() {
            val hashCollisionProblem = fixtureProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.type).isEqualTo(ProblemType.DEFINITION_RECALL)
        }

        @Test
        fun 개념명의_오타는_근접이다() {
            val hashCollisionProblem = fixtureProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.judge(AnswerText("collsion"))).isEqualTo(Judgement.NEAR_MISS)
        }

        @Test
        fun 한_글자_허용답에는_근접_판정을_하지_않는다() {
            // "큐"(길이 1)에 "규" — 정의 재생형이어도 근접이 돌면 정답의 길이·모양이 새어 나간다.
            val queueProblem = fixtureProblemOf(QUEUE)

            assertThat(queueProblem.judge(AnswerText("규"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Nested
    inner class 힌트를_로드한다 {

        @Test
        fun 힌트가_0개인_문제를_최소_하나_남긴다() {
            // 진입점 미노출 분기(hintCount==0)가 시드로도 실행되게 하려면 힌트 0개 문제가 반드시 있어야 한다.
            // 앵커 픽스처의 큐 문제가 힌트 0개를 보장한다(메인 시드 큐레이션에 흔들리지 않도록 픽스처로 고정).
            val problems = allFixtureProblems()

            assertThat(problems.any { it.hintCount == 0 }).isTrue()
        }

        @Test
        fun 힌트가_있는_문제는_약에서_강_순서로_담긴다() {
            val hashCollisionProblem = fixtureProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.hintCount).isGreaterThanOrEqualTo(2)
            // 앞에서 자를수록 부분집합이어야 한다(순서 보존).
            val firstOne = hashCollisionProblem.revealedHints(1).map { it.text }
            val firstTwo = hashCollisionProblem.revealedHints(2).map { it.text }
            assertThat(firstTwo).startsWith(*firstOne.toTypedArray())
            assertThat(firstTwo).hasSize(2)
        }

        @Test
        fun 어떤_힌트도_해설이_아니라_정답을_노출하지_않는다() {
            // 콘텐츠 신뢰성 가드레일: 힌트·교정 메시지에 허용답 문자열이 섞이면 안 된다.
            val problems = allLoadedProblems()

            problems.forEach { problem ->
                val answers = problem.acceptableAnswers.map { AnswerText(it).value }
                val texts = problem.revealedHints(problem.hintCount).map { it.text } +
                    problem.misconceptionHints.map { it.message }
                texts.forEach { text ->
                    val normalized = text.lowercase()
                    answers.forEach { answer ->
                        assertThat(normalized)
                            .`as`("힌트/교정 메시지가 정답 '%s'을(를) 노출하면 안 된다: %s", answer, text)
                            .doesNotContain(answer)
                    }
                }
            }
        }
    }

    @Nested
    inner class 심화_정보를_로드한다 {

        @Test
        fun 해시_충돌_문제는_심화_정보를_가진다() {
            val hashCollisionProblem = fixtureProblemOf(HASH_COLLISION)

            assertThat(hashCollisionProblem.enrichment).isNotNull()
        }

        @Test
        fun 스레드_문제는_심화_정보를_가진다() {
            val threadProblem = fixtureProblemOf(PROCESS_AND_THREAD)

            assertThat(threadProblem.enrichment).isNotNull()
        }

        @Test
        fun 해시_충돌_심화_정보는_시안_구조를_순서대로_따른다() {
            // 질문형 제목 + 리드 문단 + 해결책 항목(순서 보존) + 인용의 시안 원형.
            val enrichment = fixtureProblemOf(HASH_COLLISION).enrichment!!

            assertThat(enrichment.title).isEqualTo("왜 충돌이 발생할까요?")
            assertThat(enrichment.body).isNotBlank()
            assertThat(enrichment.items.map { it.title })
                .containsExactly("해결책 01. 체이닝", "해결책 02. 개방 주소법")
            assertThat(enrichment.items.all { it.description.isNotBlank() }).isTrue()
            assertThat(enrichment.quote).isNotBlank()
        }

    }

    @Nested
    inner class 대표_정답을_로드한다 {

        @Test
        fun 모든_문제가_대표_정답_불변식을_만족한다() {
            // 화면의 대표 정답을 그대로 따라 입력하면 통과해야 하므로, 정규화 기준으로 허용답에 있어야 한다.
            // (도메인 Problem의 init require가 로드 시점에 이미 강제하지만, 여기서도 명시적으로 확인한다.)
            val problems = allLoadedProblems()

            problems.forEach { problem ->
                assertThat(problem.representativeAnswer).isNotBlank()
                val normalizedAnswers = problem.acceptableAnswers.map { AnswerText(it).value }
                assertThat(AnswerText(problem.representativeAnswer).value)
                    .`as`("대표 정답 '%s'은(는) 정규화 기준으로 허용답에 있어야 한다", problem.representativeAnswer)
                    .isIn(normalizedAnswers)
            }
        }

        @Test
        fun 한영_병기가_자연스러운_문제는_병기_표기를_대표로_둔다() {
            val threadProblem = fixtureProblemOf(PROCESS_AND_THREAD)

            assertThat(threadProblem.representativeAnswer).isEqualTo("스레드 (thread)")
        }

        @Test
        fun 유도형은_표준_수식_표기를_대표로_둔다() {
            val timeComplexityProblem = fixtureProblemOf(TIME_COMPLEXITY)

            assertThat(timeComplexityProblem.representativeAnswer).isEqualTo("O(n²)")
        }
    }

    @Nested
    inner class 승인_상태를_로드한다 {

        @Test
        fun 시딩된_문제는_전부_승인_상태다() {
            // 명세: MVP는 시딩분을 승인 취급한다. 초안으로 들어가면 서빙 게이트(승인 필터)에 걸려
            // 로컬 기동에서 어떤 세션도 만들 수 없게 된다.
            val problems = allLoadedProblems()

            assertThat(problems).isNotEmpty()
            problems.forEach { problem ->
                assertThat(problem.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
            }
        }
    }

    @Nested
    inner class 시딩_허용_프로파일을_제한한다 {

        @Test
        fun 로더는_local과_tester_프로파일에서만_활성화된다() {
            // 시드는 검수 없이 승인(APPROVED)으로 들어가므로, 허용 프로파일이 늘어나면
            // "검수 전 실서비스 투입 금지" 가드레일이 뚫린다. 운영(기본 프로파일) 활성화를 여기서 막는다.
            // tester는 테스터 피드백용 배포 환경(실서비스 아님) — 오너 결정 2026-07-20.
            val profiles = ProblemDataLoader::class.java
                .getAnnotation(org.springframework.context.annotation.Profile::class.java)

            assertThat(profiles).isNotNull()
            assertThat(profiles!!.value).containsExactlyInAnyOrder("local", "tester")
        }
    }

    @Nested
    inner class 오답_교정_힌트를_로드한다 {

        @Test
        fun 스레드_문제는_프로세스_오답에_교정_힌트를_준다() {
            val threadProblem = fixtureProblemOf(PROCESS_AND_THREAD)

            val outcome = threadProblem.evaluate(AnswerText("프로세스"))

            assertThat(outcome.judgement).isEqualTo(Judgement.MISMATCH)
            assertThat(outcome.misconceptionHint).isNotNull()
        }
    }

    @Nested
    inner class 복수_개념을_태깅한다 {

        @Test
        fun 최소_한_문제는_복수_개념으로_태깅된다() {
            // 문제 N—M 개념 경로가 시드로도 실행되게 하려면 복수 개념 문제가 반드시 있어야 한다.
            val problems = allLoadedProblems()

            assertThat(problems.any { it.conceptNames().size >= 2 }).isTrue()
        }

        @Test
        fun 스레드_문제는_대표_개념을_앞에_둔_복수_개념을_가진다() {
            // 태깅 순서는 대표 개념(프로세스와 스레드)이 먼저, 그다음 각자 갖는 자원인 스택.
            val threadProblem = fixtureProblemOf(PROCESS_AND_THREAD)

            assertThat(threadProblem.conceptNames()).containsExactly(PROCESS_AND_THREAD, STACK)
        }
    }

    @Nested
    inner class 개념을_이름_기준으로_공유한다 {

        @Test
        fun 문제_간에_같은_이름의_개념은_같은_행을_공유한다() {
            // "스택"은 스레드 문제와 스택 문제 양쪽에 태깅된다. 로더 안에서 같은 이름은 한 번만 생성돼야 한다.
            given(problemRepository.count()).willReturn(0L)
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            fixtureLoader.run()

            // 앵커 픽스처의 고유 개념 이름은 5개(프로세스와 스레드·스택·큐·해시 충돌·시간 복잡도) —
            // "스택"이 스레드 문제와 스택 문제에 함께 태깅되므로, 개념 저장 횟수는 태깅 총량(6)이 아니라 고유 이름 수(5)와 같아야 한다.
            verify(conceptRepository, times(5)).save(any(Concept::class.java))
        }

        @Test
        @Suppress("UNCHECKED_CAST")
        fun DB에_이미_있는_개념은_새로_만들지_않는다() {
            given(problemRepository.count()).willReturn(0L)
            val existingStack = Concept("스택")
            given(conceptRepository.findByName(anyString())).willAnswer { invocation ->
                if (invocation.getArgument<String>(0) == "스택") existingStack else null
            }
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            fixtureLoader.run()

            val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
            verify(problemRepository).saveAll(loadedProblems.capture())
            val stackProblem = loadedProblems.value.single { it.conceptNames().contains(STACK) && it.conceptNames().size == 1 }
            assertThat(stackProblem.concepts).contains(existingStack)
            // "스택"은 이미 존재하므로 새로 save 되어서는 안 된다.
            verify(conceptRepository, org.mockito.Mockito.never()).save(existingStack)
        }
    }

    @Nested
    inner class 개념_카테고리를_로드한다 {

        private val categoryLoader = ProblemDataLoader(
            conceptRepository,
            problemRepository,
            jacksonObjectMapper(),
            resourcePath = "seed/category-backfill-problems.json",
        )

        @Suppress("UNCHECKED_CAST")
        private fun runCategoryLoader(): List<Problem> {
            given(problemRepository.count()).willReturn(0L)
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            categoryLoader.run()

            val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
            verify(problemRepository).saveAll(loadedProblems.capture())
            return loadedProblems.value
        }

        @Test
        fun conceptCategories_맵에_있는_개념은_해당_카테고리로_생성된다() {
            val problems = runCategoryLoader()

            val concept = problems.single { it.conceptNames().contains("분류된 개념") }.concepts.single()
            assertThat(concept.category).isEqualTo(ProblemCategory.DATABASE)
        }

        @Test
        fun conceptCategories_맵에_없는_개념은_미분류로_남는다() {
            // 카테고리 필드 도입 이전 시드와의 하위 호환 — 맵에 이름이 없으면 조용히 null로 생성된다.
            val problems = runCategoryLoader()

            val concept = problems.single { it.conceptNames().contains("미분류 개념") }.concepts.single()
            assertThat(concept.category).isNull()
        }
    }

    @Nested
    inner class 연결_문제_속성을_로드한다 {

        @Test
        fun integration_필드가_없으면_false로_로드된다() {
            // 하위 호환(DI12): 필드가 없는 기존 시드는 모두 미지정(integration=false)으로 로드된다.
            val problems = allFixtureProblems()

            assertThat(problems).isNotEmpty()
            assertThat(problems).allMatch { !it.integration }
        }

        @Test
        fun 계단을_갖춘_연결_문제는_지정_상태로_로드된다() {
            // 연결 문제의 각 구성 개념이 단일 개념 문제로도 다뤄지면(계단 존재) 정상 로드된다.
            val problems = loadProblemsFrom("seed/integration-with-stair.json")

            val integrationProblem = problems.single { it.integration }
            assertThat(integrationProblem.concepts.size).isGreaterThanOrEqualTo(2)
        }

        @Test
        fun 계단_없는_연결_문제_시드는_기동을_실패시킨다() {
            // 연결 문제의 구성 개념을 단일 개념 문제로도 다루지 않으면(계단 없음) 영구 잠금이 되므로 기동이 실패한다.
            val stairMissingLoader = ProblemDataLoader(
                conceptRepository,
                problemRepository,
                jacksonObjectMapper(),
                resourcePath = "seed/integration-without-stair.json",
            )
            given(problemRepository.count()).willReturn(0L)
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            assertThatThrownBy { stairMissingLoader.run() }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class 기동_시_콘텐츠를_업서트한다 {

        /** 문제 하나만 담은 전용 픽스처(`upsert-single-problem-fixture.json`) — saveAll 미호출 단정처럼 다른 문제 삽입에 흔들리면 안 되는 단정에 쓴다. */
        private val singleProblemLoader = ProblemDataLoader(
            conceptRepository,
            problemRepository,
            jacksonObjectMapper(),
            resourcePath = "seed/upsert-single-problem-fixture.json",
        )

        /** 앵커 픽스처의 스레드 문제와 질문 텍스트가 정확히 같은 기존 문제(다른 심화 정보로) 하나만 DB에 있다고 가정한다. */
        private fun existingThreadProblemWithEnrichment(enrichment: Enrichment?): Problem {
            val stack = Concept(STACK)
            val processAndThread = Concept(PROCESS_AND_THREAD)
            return Problem(
                approvalStatus = ApprovalStatus.APPROVED,
                questionText = "한 프로세스 안에서 스택 등 일부를 제외한 자원을 공유하며 실행되는 흐름의 단위는?",
                concepts = listOf(processAndThread, stack),
                acceptableAnswers = setOf("스레드", "쓰레드", "thread", "스레드 (thread)"),
                representativeAnswer = "스레드 (thread)",
                type = ProblemType.DEFINITION_RECALL,
                enrichment = enrichment,
            )
        }

        @Test
        fun 매칭되는_문제는_심화_정보만_갱신한다() {
            // given: DB에 이미 있는 문제의 심화 정보가 시드 내용과 다르다.
            val staleEnrichment = Enrichment(title = "낡은 제목", body = "낡은 본문")
            val existing = existingThreadProblemWithEnrichment(staleEnrichment)
            given(problemRepository.count()).willReturn(1L)
            given(problemRepository.findAllWithEnrichment()).willReturn(listOf(existing))
            given(conceptRepository.findByName(anyString())).willAnswer { Concept(it.getArgument(0)) }
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            // when
            fixtureLoader.run()

            // then: 같은 인스턴스의 심화 정보가 시드 내용으로 바뀐다(문제 자체를 새로 저장하지 않는다 — 매칭 발견 시 saveAll에는 신규분만 실린다).
            assertThat(existing.enrichment?.title).isEqualTo("여러 흐름이 자원을 공유하면?")
            assertThat(existing.enrichment?.items?.map { it.title }).containsExactly("동기화 도구", "문맥 교환(context switch)")
        }

        @Test
        fun 심화_정보가_동일하면_교체하지_않는다() {
            // given: DB의 심화 정보가 시드와 완전히 같다(제목·본문·항목·인용 전부 일치).
            val sameEnrichment = Enrichment(
                title = "여러 흐름이 자원을 공유하면?",
                body = "한 프로세스 안의 여러 실행 흐름이 같은 데이터를 동시에 건드리면, 실행 순서에 따라 결과가 달라지는 경쟁 상태(race condition)가 생길 수 있다.",
                items = listOf(
                    EnrichmentItem("동기화 도구", "락(lock)·세마포어로 '한 번에 하나씩만' 접근하도록 조율해 경쟁 상태를 막는다."),
                    EnrichmentItem("문맥 교환(context switch)", "여러 흐름이 CPU를 번갈아 쓰도록 상태를 저장하고 복원하는 비용이 든다."),
                ),
                quote = null,
            )
            val existing = existingThreadProblemWithEnrichment(sameEnrichment)
            given(problemRepository.count()).willReturn(1L)
            given(problemRepository.findAllWithEnrichment()).willReturn(listOf(existing))
            given(conceptRepository.findByName(anyString())).willAnswer { Concept(it.getArgument(0)) }
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            // when: 문제 하나만 담은 전용 픽스처를 구동한다 — 시드의 유일한 문제가 매칭되므로, 무변경이면 신규 삽입도 전혀 없어야 한다.
            singleProblemLoader.run()

            // then: 내용이 같으므로 참조 자체가 바뀌지 않아야 한다(불필요한 교체·쓰기가 없다는 근거).
            assertThat(existing.enrichment).isSameAs(sameEnrichment)
            // 매칭된 기존 문제만 있고 신규 문제가 없으므로 saveAll이 아예 호출되지 않아야 한다.
            verify(problemRepository, never()).saveAll(anyList())
        }

        @Test
        @Suppress("UNCHECKED_CAST")
        fun 매칭되지_않는_시드_문제는_신규_삽입한다() {
            // given: DB가 비어있지 않지만(카운트>0), 앵커 픽스처의 어떤 질문 텍스트와도 일치하는 기존 행이 없다.
            given(problemRepository.count()).willReturn(1L)
            given(problemRepository.findAllWithEnrichment()).willReturn(emptyList())
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            fixtureLoader.run()

            val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
            verify(problemRepository).saveAll(captor.capture())
            // 앵커 픽스처의 문제 5개 전부 매칭 실패로 신규 삽입 대상이 된다.
            assertThat(captor.value).hasSize(5)
        }

        @Test
        fun DB에는_있지만_시드에_없는_문제는_삭제하지_않는다() {
            // given: 시드 어디에도 없는 질문 텍스트를 가진 기존 문제.
            val orphanProblem = Problem(
                approvalStatus = ApprovalStatus.APPROVED,
                questionText = "시드에서 이미 빠진 낡은 질문",
                concepts = listOf(Concept("낡은 개념")),
                acceptableAnswers = setOf("답"),
                representativeAnswer = "답",
                type = ProblemType.DEFINITION_RECALL,
            )
            given(problemRepository.count()).willReturn(1L)
            given(problemRepository.findAllWithEnrichment()).willReturn(listOf(orphanProblem))
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            fixtureLoader.run()

            // then: 삭제 API(delete/deleteAll 등) 호출이 전혀 없어야 한다 — 로그만 남기고 보존.
            verify(problemRepository, never()).delete(any(Problem::class.java))
            verify(problemRepository, never()).deleteAll(anyIterable())
        }
    }

    @Test
    fun 잘못된_시드_데이터는_조용히_스킵되지_않는다() {
        // 대표 정답이 허용답 집합에 없는 불량 데이터(server/src/test/resources/seed/invalid-problems.json).
        // 도메인 Problem의 init require가 발동해, 조용한 스킵이 아니라 예외로 기동이 실패해야 한다.
        val invalidLoader = ProblemDataLoader(
            conceptRepository,
            problemRepository,
            jacksonObjectMapper(),
            resourcePath = "seed/invalid-problems.json",
        )
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willReturn(null)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        assertThatThrownBy { invalidLoader.run() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Nested
    inner class 승인_요건을_충족하지_못한_시드는_기동을_실패시킨다 {

        @Test
        fun 유형_태깅이_빠진_시드는_구조_단정에서_예외를_던진다() {
            // C3: 승인 상태로 곧장 생성되는 시드는 approve()의 전이 검증을 거치지 않으므로,
            // saveAll 전 assertStructurallyApprovable()이 유형 미상(type=null)을 잡아내야 한다(수용 기준 23).
            val typeMissingLoader = ProblemDataLoader(
                conceptRepository,
                problemRepository,
                jacksonObjectMapper(),
                resourcePath = "seed/type-missing-problem.json",
            )
            given(problemRepository.count()).willReturn(0L)
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            assertThatThrownBy { typeMissingLoader.run() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
                .hasMessage(Problem.TYPE_REQUIRED_MESSAGE)
        }

        @Test
        fun 힌트가_정답을_노출하는_시드는_구조_단정에서_예외를_던진다() {
            // C3: no-leak 위반도 같은 구조 단정 경로(assertStructurallyApprovable)로 잡혀야 한다(수용 기준 16).
            val answerLeakLoader = ProblemDataLoader(
                conceptRepository,
                problemRepository,
                jacksonObjectMapper(),
                resourcePath = "seed/answer-leak-problem.json",
            )
            given(problemRepository.count()).willReturn(0L)
            given(conceptRepository.findByName(anyString())).willReturn(null)
            given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

            assertThatThrownBy { answerLeakLoader.run() }
                .isInstanceOf(InvalidApprovalStateException::class.java)
                .hasMessage(Problem.ANSWER_LEAK_MESSAGE)
        }
    }

    /** 로더를 실제로 구동해, 로드된 전체 문제를 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun allLoadedProblems(): List<Problem> {
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willReturn(null)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        problemDataLoader.run()

        val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
        verify(problemRepository).saveAll(loadedProblems.capture())
        return loadedProblems.value
    }

    /** 지정한 리소스 경로의 시드를 구동해, 저장 직전 로드된 전체 문제를 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun loadProblemsFrom(resourcePath: String): List<Problem> {
        val loader = ProblemDataLoader(conceptRepository, problemRepository, jacksonObjectMapper(), resourcePath = resourcePath)
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willReturn(null)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        loader.run()

        val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
        verify(problemRepository).saveAll(loadedProblems.capture())
        return loadedProblems.value
    }

    /** 앵커 픽스처([fixtureLoader])를 구동해, 로드된 전체 문제를 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun allFixtureProblems(): List<Problem> {
        given(problemRepository.count()).willReturn(0L)
        given(conceptRepository.findByName(anyString())).willReturn(null)
        given(conceptRepository.save(any(Concept::class.java))).willAnswer { it.getArgument(0) }

        fixtureLoader.run()

        val loadedProblems = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Problem>>
        verify(problemRepository).saveAll(loadedProblems.capture())
        return loadedProblems.value
    }

    /** 앵커 픽스처([fixtureLoader])를 구동해, 지정한 개념에 대해 로드된 문제를 꺼낸다. */
    @Suppress("UNCHECKED_CAST")
    private fun fixtureProblemOf(conceptName: String): Problem =
        allFixtureProblems().single { it.conceptNames().contains(conceptName) }
}
