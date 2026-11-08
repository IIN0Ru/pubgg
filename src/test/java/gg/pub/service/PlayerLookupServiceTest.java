package gg.pub.service;

import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        when(playerService.getBasics(Platform.STEAM, "N0-RU"))
                .thenReturn(new PlayerService.Basics(
                        new PlayerMatches("N0-RU", Platform.STEAM, null, List.of()), List.of(), "account.test0001"));
    }

    @Test
    void 두_번째_검색부터는_저장된_결과를_쓴다() {
        lookupService.find(Platform.STEAM, "N0-RU");
        clock.advance(Duration.ofHours(3));
        lookupService.find(Platform.STEAM, "n0-ru");   // 대소문자가 달라도 같은 사람

        verify(playerService, times(1)).getBasics(Platform.STEAM, "N0-RU");
    }

    @Test
    void 저장된_결과만_보는_조회는_PUBG_API를_부르지_않는다() {
        assertThat(lookupService.findSaved(Platform.STEAM, "N0-RU")).isEmpty();
        verify(playerService, org.mockito.Mockito.never()).getBasics(Platform.STEAM, "N0-RU");

        lookupService.find(Platform.STEAM, "N0-RU");
        assertThat(lookupService.findSaved(Platform.STEAM, "n0-ru")).isPresent();   // 대소문자가 달라도 찾는다
        verify(playerService, times(1)).getBasics(Platform.STEAM, "N0-RU");
    }

    @Test
    void 갱신은_2분이_지나야_다시_불러온다() {
        lookupService.find(Platform.STEAM, "N0-RU");

        clock.advance(Duration.ofSeconds(30));
        assertThat(lookupService.refresh(Platform.STEAM, "N0-RU")).isFalse();   // 아직 2분 안 됨

        clock.advance(Duration.ofMinutes(2));
        assertThat(lookupService.refresh(Platform.STEAM, "N0-RU")).isTrue();

        verify(playerService, times(2)).getBasics(Platform.STEAM, "N0-RU");
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

    @Test
    void 카드는_뒤에서_만들고_전적은_먼저_돌려준다() throws Exception {
        PlayerMatches basic = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());
        PlayerService.Basics basics = new PlayerService.Basics(basic, List.of(mock(MatchRecord.class)), "account.test0001");
        PlayCard card = mock(PlayCard.class);
        CountDownLatch release = new CountDownLatch(1);
        when(playerService.getBasics(Platform.STEAM, "N0-RU")).thenReturn(basics);
        when(playerService.buildCard(basics)).thenAnswer(inv -> {
            release.await(5, TimeUnit.SECONDS);   // 카드 계산이 오래 걸리는 상황
            return card;
        });

        Lookup first = lookupService.find(Platform.STEAM, "N0-RU");
        assertThat(first.cardPending()).isTrue();                    // 전적은 먼저 나오고
        assertThat(first.result().card()).isNull();                  // 카드는 아직 없다
        assertThat(lookupService.isCardPending(Platform.STEAM, "n0-ru")).isTrue();

        release.countDown();
        Lookup done = lookupService.findWithCard(Platform.STEAM, "N0-RU");   // 끝날 때까지 기다림
        assertThat(done.cardPending()).isFalse();
        assertThat(done.result().card()).isSameAs(card);
        assertThat(lookupService.isCardPending(Platform.STEAM, "N0-RU")).isFalse();
        verify(playerService, times(1)).getBasics(Platform.STEAM, "N0-RU");
    }
}
