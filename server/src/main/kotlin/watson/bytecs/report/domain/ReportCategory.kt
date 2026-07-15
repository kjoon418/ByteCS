package watson.bytecs.report.domain

/**
 * 콘텐츠 오류 신고의 유형(단일 선택·필수). 07 시안의 4개 선택지에 대응한다.
 * 사용자에게 보이는 라벨은 클라이언트 라이팅이고, 서버는 이 코드만 저장한다.
 */
enum class ReportCategory {
    /** 정답이 틀려요. */
    WRONG_ANSWER,

    /** 문제 설명에 오류가 있어요. */
    QUESTION_ERROR,

    /** 힌트에 오류가 있어요. */
    HINT_ERROR,

    /** 기타. */
    OTHER,
    ;

    companion object {
        /** 요청 문자열을 유형으로 변환한다. 미지원 값은 400으로 거부되도록 IllegalArgumentException을 던진다. */
        fun from(raw: String): ReportCategory =
            entries.firstOrNull { it.name == raw }
                ?: throw IllegalArgumentException("지원하지 않는 신고 유형입니다: $raw")
    }
}
