package watson.bytecs.problem.infrastructure

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Hint
import watson.bytecs.problem.domain.MisconceptionHint
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType

/**
 * 애플리케이션 기동 시, 문제가 하나도 없을 때만 CS 문제를 시딩한다.
 * 테스트는 각자 필요한 데이터를 직접 준비하도록, test 프로파일에서는 동작하지 않는다.
 */
@Component
@Profile("!test")
class ProblemSeeder(
    private val conceptRepository: ConceptRepository,
    private val problemRepository: ProblemRepository,
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String?) {
        if (problemRepository.count() > 0) {
            return
        }

        val process = conceptRepository.save(
            Concept("프로세스와 스레드", "프로세스는 실행 중인 프로그램, 스레드는 프로세스 내부의 실행 흐름 단위다."),
        )
        val stack = conceptRepository.save(
            Concept("스택", "후입선출(LIFO) 구조의 선형 자료구조."),
        )
        val queue = conceptRepository.save(
            Concept("큐", "선입선출(FIFO) 구조의 선형 자료구조."),
        )
        val hashCollision = conceptRepository.save(
            Concept("해시 충돌", "서로 다른 키가 같은 해시 인덱스로 매핑되는 현상."),
        )
        val tcp = conceptRepository.save(
            Concept("TCP", "연결 지향적이고 신뢰성 있는 전송 계층 프로토콜."),
        )
        val cache = conceptRepository.save(
            Concept("캐시", "자주 쓰는 데이터를 더 빠른 저장 공간에 두어 접근 속도를 높이는 기법."),
        )
        val timeComplexity = conceptRepository.save(
            Concept("시간 복잡도", "입력 크기에 따른 연산 횟수 증가율을 빅오 표기법으로 나타낸 것."),
        )

        val problems = listOf(
            Problem(
                questionText = "한 프로세스 안에서 스택 등 일부를 제외한 자원을 공유하며 실행되는 흐름의 단위는?",
                concept = process,
                acceptableAnswers = setOf("스레드", "쓰레드", "thread"),
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "스레드는 프로세스의 코드·데이터·힙을 공유하되, 스택과 레지스터는 각자 가진다.",
                // 약→강. 정답 표기(스레드·쓰레드·thread)를 담지 않는다.
                hints = listOf(
                    Hint("실행 중인 프로그램 전체가 아니라, 그 '안에서' 도는 더 작은 실행 단위를 떠올려 보세요."),
                    Hint("한 프로그램 안에서 코드·데이터·힙을 공유하며 여럿이 동시에 흐르되, 각자 자기 스택만 따로 가지는 실행 흐름입니다."),
                ),
                // 명세 시나리오: 정답 '스레드'에 흔한 오답 '프로세스'. 교정 메시지는 정답을 말하지 않는다.
                misconceptionHints = listOf(
                    MisconceptionHint(
                        expectedAnswers = setOf("프로세스", "process"),
                        message = "프로세스는 실행 중인 프로그램 그 자체예요. 이 문제는 그 '안에서' 자원을 공유하며 도는 더 작은 실행 흐름을 묻고 있어요. 다시 도전해보세요!",
                    ),
                ),
            ),
            Problem(
                questionText = "가장 나중에 넣은 데이터가 가장 먼저 나오는 후입선출(LIFO) 자료구조는?",
                concept = stack,
                acceptableAnswers = setOf("스택", "stack"),
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "함수 호출 스택, 되돌리기(undo) 등에 쓰인다.",
            ),
            Problem(
                questionText = "먼저 넣은 데이터가 먼저 나오는 선입선출(FIFO) 자료구조는?",
                concept = queue,
                acceptableAnswers = setOf("큐", "queue"),
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "BFS 탐색, 작업 대기열 등에 쓰인다.",
            ),
            Problem(
                questionText = "서로 다른 키가 해시 함수를 통해 동일한 인덱스로 매핑되는 현상을 무엇이라 하는가?",
                concept = hashCollision,
                acceptableAnswers = setOf("해시 충돌", "해시충돌", "충돌", "collision", "hash collision"),
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.MEDIUM,
                explanation = "체이닝, 개방 주소법 등으로 해소한다.",
                // 약→강. 정답 표기(충돌·collision 등)를 담지 않는다.
                hints = listOf(
                    Hint("서로 다른 두 입력이 해시 함수를 거쳐 같은 칸을 가리키면 어떤 일이 벌어질까요?"),
                    Hint("이 현상을 해소하려고 체이닝·개방 주소법을 씁니다. 그 '현상 자체'의 이름을 답하세요."),
                ),
            ),
            Problem(
                questionText = "3-way handshake로 연결을 수립하고 데이터 전달의 신뢰성을 보장하는 전송 계층 프로토콜은?",
                concept = tcp,
                acceptableAnswers = setOf("tcp", "티씨피"),
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.MEDIUM,
                explanation = "UDP와 달리 연결 지향적이며 순서·재전송을 보장한다.",
            ),
            Problem(
                questionText = "자주 사용하는 데이터를 더 빠른 저장 공간에 미리 저장해 접근 속도를 높이는 기법은?",
                concept = cache,
                acceptableAnswers = setOf("캐시", "캐싱", "cache", "caching"),
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "지역성(locality)을 활용해 평균 접근 시간을 줄인다.",
            ),
            Problem(
                questionText = "다음 코드의 시간 복잡도를 빅오 표기법으로 나타내면?",
                concept = timeComplexity,
                // 유도형은 근접 판정이 없으니, 같은 답의 다른 표기는 오직 여기 등재로만 인정된다.
                // 지수(^2·²·*n·제곱)와 빅오 괄호 유무를 조합한 표기를 모두 채운다.
                // ([AnswerText]가 대소문자·공백만 정규화하고 구두점은 접지 않으므로, 표기마다 별도 등재가 필요하다)
                acceptableAnswers = setOf(
                    "o(n^2)", "o(n²)", "o(n*n)", "o(n제곱)",
                    "n^2", "n²", "n*n", "n제곱",
                    "오엔제곱",
                ),
                type = ProblemType.DERIVATION,
                difficulty = Difficulty.MEDIUM,
                codeSnippet = """
                    for (i in 0 until n) {
                        for (j in 0 until n) {
                            println(i * j)
                        }
                    }
                """.trimIndent(),
                explanation = "이중 반복문이 각각 n번 돌아 n×n = n² 번 수행된다.",
                // 약→강. 유도형이라 정답 표기(o(n^2)·n² 등)는 담지 않고 세는 방법만 짚는다.
                hints = listOf(
                    Hint("바깥 반복과 안쪽 반복이 각각 몇 번 도는지 따로 세어, 둘을 곱해 보세요."),
                    Hint("바깥이 입력 크기만큼, 그 각각에서 안쪽이 다시 입력 크기만큼 돕니다. 전체 수행 횟수를 빅오로 표기하세요."),
                ),
            ),
        )
        problemRepository.saveAll(problems)
    }
}
