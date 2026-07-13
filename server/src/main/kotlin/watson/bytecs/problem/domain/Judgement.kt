package watson.bytecs.problem.domain

/**
 * 정답 판정 결과. 결정적으로 산출된다(같은 입력 → 같은 판정, AI 판정 없음).
 */
enum class Judgement {
    /** 허용답과 정규화 후 정확히 일치한다. */
    CORRECT,

    /** 허용답과 불일치하지만 오탈자 수준(편집거리 임계 내)으로 가깝다. */
    NEAR_MISS,

    /** 허용답과 불일치한다. */
    MISMATCH,
}
