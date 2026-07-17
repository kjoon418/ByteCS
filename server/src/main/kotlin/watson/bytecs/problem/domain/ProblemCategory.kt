package watson.bytecs.problem.domain

/**
 * 문제(엄밀히는 [Concept])가 속하는 CS 대분류(명세 §7 '카테고리 체계', 결정 2026-07-17).
 *
 * 카테고리는 [Concept]에 귀속된다(카테고리 1 — 개념 N). 개념이 이미 복습·힌트·진도의 축이므로,
 * 대분류를 개념 위에 얹어 단일 출처로 관리한다(문제마다 따로 붙이지 않는다).
 * 이름을 `Category`가 아닌 `ProblemCategory`로 둔 이유는 신고 도메인의 [watson.bytecs.report.domain.ReportCategory]와
 * 혼동되지 않게 하기 위함이다(용도가 전혀 다르다 — 하나는 콘텐츠 오류 신고 유형, 하나는 CS 지식 대분류).
 */
enum class ProblemCategory {
    DATA_STRUCTURE,
    ALGORITHM,
    OPERATING_SYSTEM,
    NETWORK,
    DATABASE,
    COMPUTER_ARCHITECTURE,
    SOFTWARE_ENGINEERING,
    SECURITY,
}
