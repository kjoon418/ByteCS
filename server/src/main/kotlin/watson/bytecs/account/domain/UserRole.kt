package watson.bytecs.account.domain

/**
 * 사용자 유형.
 * GUEST는 가입 없이 학습 상태를 쌓고, 가입 시 같은 식별자를 유지한 채 MEMBER로 승격된다(학습 상태 승계).
 * ADMIN은 콘텐츠를 등록·검수하는 운영 주체(큐레이터 겸임 — MVP는 단일 역할, CURATOR 분리는 로드맵)로,
 * 자가 가입이 없고 기동 시 부트스트랩([watson.bytecs.admin.AdminAccountBootstrap])으로만 생성되며
 * 관리자 페이지(`/admin` 하위 경로)에 폼 로그인으로 접근한다.
 */
enum class UserRole {
    GUEST,
    MEMBER,
    ADMIN,
}
