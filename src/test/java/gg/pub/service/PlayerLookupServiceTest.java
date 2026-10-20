package gg.pub.service;

import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.service.PlayerLookupService.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검색 결과 저장과 갱신 대기 시간 확인. 시간은 직접 돌릴 수 있는 가짜 시계로 테스트한다.
 */
class PlayerLookupServiceTest {

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private PlayerService playerService;
    private TestClock clock;
    private PlayerLookupService lookupService;

    @BeforeEach
    void setUp() {
        playerService = mock(PlayerService.class);
        clock = new TestClock();
        lookupService = new PlayerLookupService(playerService, clock);
        when(playerService.getRecentMatches(Platform.STEAM, "N0-RU"))
                .thenReturn(new PlayerMatches("N0-RU", Platform.STEAM, null, List.of()));
    }

    @Test
    void 두_번째_검색부터는_저장된_결과를_쓴다() {
        lookupService.find(Platform.STEAM, "N0-RU");
        clock.advance(Duration.ofHours(3));
        lookupService.find(Platform.STEAM, "n0-ru");   // 대소문자가 달라도 같은 사람

        verify(playerService, times(1)).getRecentMatches(Platform.STEAM, "N0-RU");
    }

    @Test
    void 갱신은_2분이_지나야_다시_불러온다() {
        lookupService.find(Platform.STEAM, "N0-RU");

        clock.advance(Duration.ofSeconds(30));
        assertThat(lookupService.refresh(Platform.STEAM, "N0-RU")).isFalse();   // 아직 2분 안 됨

        clock.advance(Duration.ofMinutes(2));
        assertThat(lookupService.refresh(Platform.STEAM, "N0-RU")).isTrue();

        verify(playerService, times(2)).getRecentMatches(Platform.STEAM, "N0-RU");
    }

    @Test
    void 남은_대기_시간과_갱신_시각_문구() {
        Lookup lookup = lookupService.find(Platform.STEAM, "N0-RU");
        assertThat(lookupService.secondsUntilRefresh(lookup)).isEqualTo(120);
        assertThat(lookupService.updatedAgoText(lookup)).isEqualTo("방금 전");

        clock.advance(Duration.ofSeconds(100));
        assertThat(lookupService.secondsUntilRefresh(lookup)).isEqualTo(20);

        clock.advance(Duration.ofMinutes(5));
        assertThat(lookupService.secondsUntilRefresh(lookup)).isZero();
        assertThat(lookupService.updatedAgoText(lookup)).isEqualTo("6분 전");

        clock.advance(Duration.ofHours(2));
        assertThat(lookupService.updatedAgoText(lookup)).isEqualTo("2시간 전");
    }
}
