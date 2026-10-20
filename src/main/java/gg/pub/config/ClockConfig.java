package gg.pub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각을 알려주는 Clock.
 * 코드에서 Instant.now() 를 직접 부르지 않고 이걸 받아 쓰면, 테스트에서 시간을 마음대로 돌릴 수 있다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
