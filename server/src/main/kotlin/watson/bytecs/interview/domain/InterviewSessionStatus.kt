package watson.bytecs.interview.domain

/** 면접 세션의 진행 상태. 재제출이 없어([InterviewSession] KDoc) 세션 상태는 이 둘만으로 충분하다. */
enum class InterviewSessionStatus {
    IN_PROGRESS,
    COMPLETED,
}
