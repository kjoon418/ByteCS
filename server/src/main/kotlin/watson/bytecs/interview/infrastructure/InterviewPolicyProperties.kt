package watson.bytecs.interview.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 면접 세션 이용 정책(계획 §3.3 — 회원 전용·일일 제한). 도메인 규칙 자체는 그대로 두고, **환경별로 조일지 풀지**만 설정으로 가른다.
 *
 * ⭐️ 테스터 공개 기간 임시 완화(2026-07-24 오너 결정): 테스터에게 면접 기능을 넓게 노출·피드백받기 위해 tester 프로파일에서
 *    [memberOnly]=false(게스트도 이용) · [dailyQuota] 대폭 상향(사실상 무제한)으로 푼다. 기본값(로컬·운영)은 원래 규칙을 유지하므로,
 *    나중에 되돌릴 땐 tester yml의 이 블록만 지우면 된다(코드 무변경).
 */
@ConfigurationProperties(prefix = "bytecs.interview.policy")
data class InterviewPolicyProperties(
    /** 회원 전용 여부(기본 true — 계획 §3.3). false면 게스트도 면접 세션을 이용할 수 있다(테스터 임시 완화). */
    val memberOnly: Boolean = true,
    /**
     * 하루 최대 '채점 성공 세션' 수(기본 1 — 계획 §3.3). 이 수에 도달하면 그날 새 세션 생성이 막힌다(재개는 가능).
     * 사실상 무제한으로 두려면 아주 큰 값을 준다(테스터 프로파일). 세션당 문제 수(SESSION_SIZE)와는 별개다.
     */
    val dailyQuota: Long = 1,
)
