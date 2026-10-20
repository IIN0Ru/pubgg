package gg.pub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
import gg.pub.domain.SeasonRank;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.pubgapi.dto.RankedStatsResponse;
import gg.pub.pubgapi.dto.SeasonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이번 시즌 티어 가져오기 확인.
 *
 * 예시 ranked.json: squad-fpp 120판 플래티넘 3 (2450 RP), squad 8판 골드 1
 *  → 판 수가 많은 squad-fpp 를 보여준다. 평균 딜 = 33600.4 ÷ 120 = 280
 */
class SeasonServiceTest {

    private static final String SEASON = "division.bro.official.pc-2018-36";

    private final ObjectMapper mapper = new ObjectMapper();
    private PubgApiClient client;
    private MovableClock clock;
    private SeasonService service;

    /** 테스트에서 시간을 직접 돌리는 시계 */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-20T00:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach
    void setUp() throws IOException {
        client = mock(PubgApiClient.class);
        clock = new MovableClock();
        service = new SeasonService(client, clock);
        when(client.getSeasons(Platform.STEAM)).thenReturn(read("/pubg/seasons.json", SeasonResponse.class));
        when(client.getRankedStats(eq(Platform.STEAM), anyString(), eq(SEASON)))
                .thenReturn(read("/pubg/ranked.json", RankedStatsResponse.class));
    }

    private <T> T read(String path, Class<T> type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return mapper.readValue(in, type);
        }
    }

    @Test
    void 판_수가_많은_모드의_티어를_보여준다() {
        SeasonRank rank = service.currentRank(Platform.STEAM, "account.test0001").orElseThrow();

        assertThat(rank.gameMode()).isEqualTo("squad-fpp");
        assertThat(rank.tierLabel()).isEqualTo("플래티넘 3");
        assertThat(rank.rankPointText()).isEqualTo("2,450 RP");
        assertThat(rank.rounds()).isEqualTo(120);
        assertThat(rank.kdaText()).isEqualTo("2.76");
        assertThat(rank.avgDamage()).isEqualTo(280);
        assertThat(rank.top10Percent()).isEqualTo(61);
    }

    @Test
    void 시즌_ID_는_6시간_동안_다시_묻지_않는다() {
        service.currentRank(Platform.STEAM, "a");
        clock.now = clock.now.plus(Duration.ofHours(5));
        service.currentRank(Platform.STEAM, "b");
        verify(client, times(1)).getSeasons(Platform.STEAM);

        clock.now = clock.now.plus(Duration.ofHours(2));
        service.currentRank(Platform.STEAM, "c");
        verify(client, times(2)).getSeasons(Platform.STEAM);
    }

    @Test
    void 이번_시즌_기록이_없으면_빈_값() {
        when(client.getRankedStats(eq(Platform.STEAM), eq("account.new"), eq(SEASON)))
                .thenReturn(new RankedStatsResponse(null));

        assertThat(service.currentRank(Platform.STEAM, "account.new")).isEmpty();
    }

    @Test
    void 오류가_나도_검색은_깨지지_않게_빈_값() {
        when(client.getRankedStats(eq(Platform.STEAM), eq("account.x"), eq(SEASON)))
                .thenThrow(new PubgApiException("검색이 너무 많아요."));

        assertThat(service.currentRank(Platform.STEAM, "account.x")).isEmpty();
    }

    @Test
    void 티어_이름() {
        assertThat(new SeasonRank("squad", "Master", "1", 3600, 50, 3, 400, 5, 70).tierLabel()).isEqualTo("마스터");
        assertThat(new SeasonRank("squad", "Unranked", "", 0, 3, 1, 100, 20, 10).tierLabel()).isEqualTo("배치 중");
        assertThat(new SeasonRank("squad", "Crystal", "2", 2900, 50, 3, 400, 5, 70).tierLabel()).isEqualTo("크리스탈 2");
    }

    @Test
    void 최근_10경기와_시즌을_비교한다() {
        // 최근: 킬 20 + 어시 6 ÷ 죽은 판 8 = 3.25, 평균 딜 310
        PlayCard card = new PlayCard(10, 20, 6, 8, 310, "FPP", List.of(), 0, 0, List.of(), null, null, null);
        SeasonRank season = new SeasonRank("squad-fpp", "Platinum", "3", 2450, 120, 2.76, 330, 7.4, 61);

        List<SeasonRank.Comparison> rows = season.compareWith(card);

        assertThat(rows).extracting(SeasonRank.Comparison::label).containsExactly("KDA", "평균 딜량");
        assertThat(rows.get(0).recent()).isEqualTo("3.25");
        assertThat(rows.get(0).arrow()).isEqualTo("▲");
        assertThat(rows.get(1).arrow()).isEqualTo("▼");
        assertThat(season.compareWith(null)).isEmpty();
    }
}
