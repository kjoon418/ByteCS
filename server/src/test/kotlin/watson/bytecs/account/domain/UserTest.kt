package watson.bytecs.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import watson.bytecs.problem.domain.Difficulty
import java.time.LocalDate

class UserTest {

    @Nested
    inner class 게스트를_생성한다 {

        @Test
        fun 역할은_GUEST이고_이메일과_비밀번호가_없다() {
            val guest = User.createGuest()

            assertThat(guest.role).isEqualTo(UserRole.GUEST)
            assertThat(guest.isMember).isFalse()
            assertThat(guest.email).isNull()
            assertThat(guest.passwordHash).isNull()
        }

        @Test
        fun 설정은_기본값을_가진다() {
            val guest = User.createGuest()

            assertThat(guest.settings).isEqualTo(UserSettings.default())
        }
    }

    @Nested
    inner class 게스트를_회원으로_승격한다 {

        @Test
        fun 역할과_이메일과_비밀번호가_채워진다() {
            val guest = User.createGuest()

            guest.promoteToMember(Email("member@bytecs.dev"), "hashed")

            assertThat(guest.role).isEqualTo(UserRole.MEMBER)
            assertThat(guest.isMember).isTrue()
            assertThat(guest.email).isEqualTo("member@bytecs.dev")
            assertThat(guest.passwordHash).isEqualTo("hashed")
        }

        @Test
        fun 이미_회원이면_예외를_던진다() {
            val member = User.createMember(Email("member@bytecs.dev"), "hashed")

            assertThatThrownBy { member.promoteToMember(Email("other@bytecs.dev"), "hashed2") }
                .isInstanceOf(InvalidUserStateException::class.java)
        }

        @Test
        fun 승격해도_난이도_제안_응답_상태가_승계된다() {
            val guest = User.createGuest()
            guest.markDifficultyPromptDone()

            guest.promoteToMember(Email("member@bytecs.dev"), "hashed")

            // in-place 승격(같은 인스턴스·id)이라 제안 응답 상태가 그대로 남는다.
            assertThat(guest.difficultyPromptDone).isTrue()
        }
    }

    @Nested
    inner class 선호_난이도와_제안_상태를_관리한다 {

        @Test
        fun 새_게스트는_선호_미설정이고_제안에_응답하지_않았다() {
            val guest = User.createGuest()

            assertThat(guest.settings.preferredDifficulty).isNull()
            assertThat(guest.difficultyPromptDone).isFalse()
            assertThat(guest.needsDifficultyPrompt()).isTrue()
        }

        @Test
        fun 선호_난이도를_지정하면_제안_노출도_종료된다() {
            val user = User.createGuest()

            user.updatePreferredDifficulty(Difficulty.EASY)

            assertThat(user.settings.preferredDifficulty).isEqualTo(Difficulty.EASY)
            assertThat(user.difficultyPromptDone).isTrue()
            assertThat(user.needsDifficultyPrompt()).isFalse()
        }

        @Test
        fun 제안을_거절해_응답만_기록하면_선호는_미설정으로_남고_다시_묻지_않는다() {
            val user = User.createGuest()

            user.markDifficultyPromptDone()

            assertThat(user.settings.preferredDifficulty).isNull()
            assertThat(user.difficultyPromptDone).isTrue()
            assertThat(user.needsDifficultyPrompt()).isFalse()
        }

        @Test
        fun 세션_분량을_바꿔도_선호_난이도는_보존된다() {
            val user = User.createGuest()
            user.updatePreferredDifficulty(Difficulty.HARD)

            user.updateDailySessionSize(20)

            assertThat(user.settings.dailySessionSize).isEqualTo(20)
            assertThat(user.settings.preferredDifficulty).isEqualTo(Difficulty.HARD)
        }
    }

    @Nested
    inner class 학습을_기록해_스트릭을_갱신한다 {

        private val today: LocalDate = LocalDate.of(2026, 7, 14)

        @Test
        fun 첫_학습이면_스트릭이_1이_된다() {
            val user = User.createGuest()

            user.recordStudy(today)

            assertThat(user.streak.count).isEqualTo(1)
            assertThat(user.streak.lastStudyDate).isEqualTo(today)
        }

        @Test
        fun 같은_날_다시_기록해도_스트릭은_그대로다() {
            val user = User.createGuest()
            user.recordStudy(today)

            user.recordStudy(today)

            assertThat(user.streak.count).isEqualTo(1)
        }

        @Test
        fun 이틀_연속_학습하면_스트릭이_2가_된다() {
            val user = User.createGuest()
            user.recordStudy(today.minusDays(1))

            user.recordStudy(today)

            assertThat(user.streak.count).isEqualTo(2)
            assertThat(user.streak.lastStudyDate).isEqualTo(today)
        }

        @Test
        fun 하루_이상_건너뛰면_스트릭이_1로_리셋된다() {
            val user = User.createGuest()
            user.recordStudy(today.minusDays(2))

            user.recordStudy(today)

            assertThat(user.streak.count).isEqualTo(1)
        }
    }
}
