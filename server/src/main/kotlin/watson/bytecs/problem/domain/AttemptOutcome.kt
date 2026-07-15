package watson.bytecs.problem.domain

/**
 * 한 번의 제출을 판정한 결과. 판정([judgement])과, 예상 오답에 매칭됐을 때만 실리는 오답 교정 힌트([misconceptionHint])를 묶는다.
 * 오답 교정 힌트가 매칭되면 판정은 MISMATCH로 확정된다(근접보다 우선) — 자세한 근거는 [Problem.evaluate].
 */
data class AttemptOutcome(
    val judgement: Judgement,
    val misconceptionHint: String?,
)
