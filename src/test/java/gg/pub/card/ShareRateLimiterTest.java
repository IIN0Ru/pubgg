package gg.pub.card;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** 공유 카드 만들기 횟수 제한 */
class ShareRateLimiterTest {

    /** 테스트에서 시간을 직접 돌리는 시계 */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-07T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final MutableClock clock = new MutableClock();
    final ShareRateLimiter limiter = new ShareRateLimiter(clock, 3, Duration.ofMinutes(10));

    @Test
    void 한도까지는_허용하고_넘으면_막는다() {
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    void 접속_주소마다_따로_센다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();
    }

    @Test
    void 시간이_지나면_다시_허용한다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();

        clock.now = clock.now.plus(Duration.ofMinutes(10)).plusSeconds(1);
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }

    @Test
    void 막힌_시도는_횟수에_세지_않는다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("1.1.1.1");
        }
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();   // 5분째: 아직 3번이 창 안에 있다

        clock.now = clock.now.plus(Duration.ofMinutes(5)).plusSeconds(1);   // 처음 3번이 창 밖으로 나감
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }
}
