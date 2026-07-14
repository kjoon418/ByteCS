package watson.bytecs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * 시간 의존을 하나의 주입 가능한 [Clock]으로 모은다.
 * 하루 경계(오늘의 세션·스트릭)는 서비스 표준 시간대인 Asia/Seoul(KST) 기준이며,
 * 도메인·서비스가 LocalDate.now()를 직접 부르지 않고 이 시계를 통해서만 오늘을 읽게 해, 테스트에서 날짜를 결정적으로 고정할 수 있다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
