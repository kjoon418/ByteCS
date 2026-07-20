package watson.bytecs.account.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.application.dto.GuestResponse
import watson.bytecs.account.application.dto.TokenResponse
import watson.bytecs.account.application.dto.UserResponse
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.EmailDuplicatedException
import watson.bytecs.account.domain.InvalidCredentialsException
import watson.bytecs.account.domain.RawPassword
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.domain.UserSettings
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.account.security.JwtTokenProvider
import watson.bytecs.report.infrastructure.ContentReportRepository
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import watson.bytecs.scrap.infrastructure.ScrapRepository
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 계정 발급·가입·로그인·설정·삭제를 조율한다.
 * 상태 전이 규칙(게스트→회원 승격 등)은 도메인(User)에 위임하고, 서비스는 저장·토큰 발급·응답 변환만 담당한다.
 * 조회가 기본이라 클래스는 읽기 전용 트랜잭션으로 두고, 상태를 바꾸는 메서드만 쓰기 트랜잭션으로 재정의한다.
 */
@Service
@Transactional(readOnly = true)
class AccountService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val responseMapper: AccountResponseMapper,
    private val scrapRepository: ScrapRepository,
    private val sessionRepository: SessionRepository,
    private val conceptMasteryRepository: ConceptMasteryRepository,
    private val contentReportRepository: ContentReportRepository,
) {

    @Transactional
    fun createGuest(): GuestResponse {
        val guest = userRepository.save(User.createGuest())
        val token = jwtTokenProvider.issue(guest.id, guest.role)

        return responseMapper.toGuestResponse(token, guest)
    }

    /**
     * 회원 가입. 게스트 토큰을 함께 보내면 새 계정을 만들지 않고 그 게스트를 승격해 학습 상태를 승계한다.
     * 이메일 중복은 열거 방지와 무관한 정상 검증이므로 명시적으로 409를 던진다.
     */
    @Transactional
    fun register(email: Email, rawPassword: RawPassword, currentUserId: Long?): TokenResponse {
        if (userRepository.findByEmail(email.value) != null) {
            throw EmailDuplicatedException(email)
        }

        val passwordHash = passwordEncoder.encode(rawPassword.value)
        val member = try {
            if (currentUserId != null) {
                // 게스트 승격: 같은 id를 유지하므로 그 id에 쌓인 학습 상태가 그대로 넘어온다.
                val user = userRepository.findById(currentUserId)
                    .orElseThrow { UserNotFoundException.byId(currentUserId) }
                user.promoteToMember(email, passwordHash)
                userRepository.saveAndFlush(user)
            } else {
                userRepository.saveAndFlush(User.createMember(email, passwordHash))
            }
        } catch (e: DataIntegrityViolationException) {
            // 사전 검사(findByEmail)와 저장 사이의 경합(TOCTOU)으로 email unique 제약이 최종 방어선이 될 때,
            // 이메일 중복이라는 도메인 의미를 여기서 부여한다(전역 DIV 핸들러는 중립 CONFLICT라 이 의미를 알지 못한다).
            // saveAndFlush로 제약 위반을 이 트랜잭션 경계 안에서 즉시 표출해 잡는다.
            throw EmailDuplicatedException(email)
        }

        val token = jwtTokenProvider.issue(member.id, UserRole.MEMBER)
        return TokenResponse(token)
    }

    /**
     * 로그인. 이메일 없음과 비밀번호 불일치를 동일한 예외로 처리해 계정 열거를 막는다.
     */
    fun login(email: Email, rawPassword: RawPassword): TokenResponse {
        val user = userRepository.findByEmail(email.value)
        val passwordHash = user?.passwordHash
        if (passwordHash == null) {
            // 사용자·해시가 없어도 더미 해시로 매칭을 수행해, 존재하지 않는 이메일과 비밀번호 불일치의 응답 시간을 맞춘다(타이밍 기반 열거 방지).
            passwordEncoder.matches(rawPassword.value, DUMMY_HASH)
            throw InvalidCredentialsException()
        }
        if (!passwordEncoder.matches(rawPassword.value, passwordHash)) {
            throw InvalidCredentialsException()
        }

        val token = jwtTokenProvider.issue(user.id, user.role)
        return TokenResponse(token)
    }

    fun getMe(userId: Long): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byId(userId) }

        return responseMapper.toUserResponse(user)
    }

    @Transactional
    fun updateSettings(userId: Long, dailySessionSize: Int): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byId(userId) }

        // 범위 검증은 UserSettings 생성 시점에 강제된다(위반 시 400).
        user.updateSettings(UserSettings(dailySessionSize))
        return responseMapper.toUserResponse(user)
    }

    @Transactional
    fun deleteMe(userId: Long) {
        // 존재하지 않는 사용자 삭제는 deleteById가 500(EmptyResultDataAccessException)을 내므로,
        // 먼저 조회해 없으면 404(UserNotFound)로 일관되게 응답한다(중복 삭제에도 500 없이).
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byId(userId) }
        // 스크랩·세션·개념 숙련도는 학습 상태의 일부라 계정과 함께 삭제한다(도메인 [결정]). 사용자 삭제 전에 지운다.
        scrapRepository.deleteByUserId(userId)
        sessionRepository.deleteByUserId(userId)
        conceptMasteryRepository.deleteByUserId(userId)
        // 신고는 학습 상태가 아니라 콘텐츠 품질 운영 데이터라 삭제하지 않고 익명화만 한다(D10).
        contentReportRepository.anonymizeByUserId(userId)
        userRepository.delete(user)
    }

    companion object {
        // 존재하지 않는 이메일 로그인 시에도 실제 BCrypt 검증을 한 번 수행하기 위한 더미 해시(cost 10).
        // 값 자체는 어떤 입력과도 매칭되지 않으며, 오직 응답 시간을 일반 로그인과 유사하게 맞추는 용도다.
        private const val DUMMY_HASH =
            "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}
