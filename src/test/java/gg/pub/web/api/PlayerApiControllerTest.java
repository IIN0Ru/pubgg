package gg.pub.web.api;

import gg.pub.domain.Clan;
import gg.pub.domain.Farming;
import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
import gg.pub.domain.PlayerMatches;
import gg.pub.domain.SeasonRank;
import gg.pub.pubgapi.PlayerNotFoundException;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.pubgapi.PubgRateLimitException;
import gg.pub.service.PlayerLookupService;
import gg.pub.service.PlayerLookupService.Lookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * JSON API 주소별 동작. PlayerLookupService 는 가짜로 바꿔서 실제 PUBG API 를 부르지 않는다.
 * 프론트(frontend/src/api/types.ts)가 기대하는 JSON 모양이 깨지지 않는지도 여기서 지킨다.
 */
@WebMvcTest(PlayerApiController.class)
class PlayerApiControllerTest {

    private static final Instant UPDATED = Instant.parse("2026-11-08T01:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlayerLookupService lookupService;

    private static MatchRecord game(String type) {
        MatchPlayer me = new MatchPlayer("N0-RU", "account.me", 3, 2, 1, 412, 1, 1500, 1, 87, 0, "alive");
        MatchPlayer mate = new MatchPlayer("Duo", "account.duo", 1, 1, 0, 150, 1, 1400, 0, 0, 0, "alive");
        return new MatchRecord("match-1", Instant.parse("2026-11-07T12:34:00Z"), "squad-fpp", "Baltic_Main",
                type, 16, null, me, List.of(mate));
    }

    private static PlayCard card() {
        return new PlayCard(1, 3, 1, 0, 412, "FPP", List.of(), 1.0, 1,
                40, List.of(40), 70, List.of(70), new Farming("대도시파밍", null, 1, 1, 1, 2.0, 1.0, 100));
    }

    private static SeasonRank season() {
        return new SeasonRank("squad-fpp", "Platinum", "3", 2450, 120, 2.76, 380, 5.2, 41);
    }

    private Lookup lookup(PlayCard card, boolean pending) {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, card, season(),
                List.of(game("competitive"), game("official")), new Clan("헬멧연구소", "HLMT"));
        return new Lookup(result, UPDATED, pending);
    }

    @Test
    void 전적을_JSON_으로_돌려준다() throws Exception {
        Lookup lookup = lookup(card(), false);
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);
        when(lookupService.secondsUntilRefresh(lookup)).thenReturn(95L);

        mvc.perform(get("/api/players/steam/N0-RU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("N0-RU"))
                .andExpect(jsonPath("$.platform.shard").value("steam"))
                .andExpect(jsonPath("$.platform.label").value("스팀"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-08T01:00:00Z"))
                .andExpect(jsonPath("$.refreshAvailableInSec").value(95))
                .andExpect(jsonPath("$.cardStatus").value("ready"))
                .andExpect(jsonPath("$.season.tierLabel").value("플래티넘 3"))
                .andExpect(jsonPath("$.season.rankPoint").value(2450))
                .andExpect(jsonPath("$.card.scope").value("competitive"))
                .andExpect(jsonPath("$.card.styleTags[0]").value("센터 운영"))
                .andExpect(jsonPath("$.card.zone.edgePercent").value(40))
                .andExpect(jsonPath("$.card.farming.farmStyle").value("대도시파밍"))
                .andExpect(jsonPath("$.shareCard.nickname").value("N0-RU"))
                .andExpect(jsonPath("$.clan.label").value("[HLMT] 헬멧연구소"))
                .andExpect(jsonPath("$.matches.length()").value(2));
    }

    @Test
    void 경기는_원시값으로_내려가고_리플레이는_경쟁전만_볼_수_있다() throws Exception {
        Lookup lookup = lookup(card(), false);
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);

        mvc.perform(get("/api/players/steam/N0-RU"))
                .andExpect(jsonPath("$.matches[0].matchId").value("match-1"))
                .andExpect(jsonPath("$.matches[0].playedAt").value("2026-11-07T12:34:00Z"))
                .andExpect(jsonPath("$.matches[0].type").value("competitive"))
                .andExpect(jsonPath("$.matches[0].perspective").value("fpp"))
                .andExpect(jsonPath("$.matches[0].modeLabel").value("스쿼드 FPP"))
                .andExpect(jsonPath("$.matches[0].rank").value(1))
                .andExpect(jsonPath("$.matches[0].teams").value(16))
                .andExpect(jsonPath("$.matches[0].replayAvailable").value(true))
                .andExpect(jsonPath("$.matches[0].me.accountId").value("account.me"))
                .andExpect(jsonPath("$.matches[0].me.damage").value(412))
                .andExpect(jsonPath("$.matches[0].teammates[0].name").value("Duo"))
                .andExpect(jsonPath("$.matches[1].type").value("official"))
                .andExpect(jsonPath("$.matches[1].replayAvailable").value(false));
    }

    @Test
    void 카드를_계산_중이면_pending_이고_카드_값은_비어_있다() throws Exception {
        Lookup lookup = lookup(null, true);
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);

        mvc.perform(get("/api/players/steam/N0-RU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardStatus").value("pending"))
                .andExpect(jsonPath("$.card").doesNotExist())
                .andExpect(jsonPath("$.shareCard").doesNotExist())
                .andExpect(jsonPath("$.matches.length()").value(2));
    }

    @Test
    void 카드를_만들_기록이_없으면_none() throws Exception {
        Lookup lookup = lookup(null, false);
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);

        mvc.perform(get("/api/players/steam/N0-RU"))
                .andExpect(jsonPath("$.cardStatus").value("none"));
    }

    @Test
    void 모르는_플랫폼은_400() throws Exception {
        mvc.perform(get("/api/players/xbox/N0-RU"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(lookupService, never()).find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 공백_닉네임은_400() throws Exception {
        mvc.perform(get("/api/players/steam/{nickname}", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void 플레이어가_없으면_404() throws Exception {
        when(lookupService.find(Platform.KAKAO, "nobody")).thenThrow(new PlayerNotFoundException("nobody"));

        mvc.perform(get("/api/players/kakao/nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.retryAfterSec").doesNotExist());
    }

    @Test
    void PUBG_요청_한도를_넘으면_429_와_다시_시도할_시간() throws Exception {
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenThrow(new PubgRateLimitException());

        mvc.perform(get("/api/players/steam/N0-RU"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSec").value(60));
    }

    @Test
    void 그_밖의_PUBG_서버_문제는_502() throws Exception {
        when(lookupService.find(Platform.STEAM, "N0-RU"))
                .thenThrow(new PubgApiException("PUBG 서버에 연결하지 못했어요."));

        mvc.perform(get("/api/players/steam/N0-RU"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    }

    @Test
    void 갱신하면_새로_불러왔는지와_갱신된_전적을_돌려준다() throws Exception {
        Lookup lookup = lookup(card(), false);
        when(lookupService.refresh(Platform.STEAM, "N0-RU")).thenReturn(true);
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);
        when(lookupService.secondsUntilRefresh(lookup)).thenReturn(120L);

        mvc.perform(post("/api/players/steam/N0-RU/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").value(true))
                .andExpect(jsonPath("$.player.nickname").value("N0-RU"))
                .andExpect(jsonPath("$.player.refreshAvailableInSec").value(120));
    }

    @Test
    void 대기_시간_안의_갱신은_refreshed_false_로_저장본을_준다() throws Exception {
        Lookup lookup = lookup(card(), false);
        when(lookupService.refresh(Platform.STEAM, "N0-RU")).thenReturn(false);
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);
        when(lookupService.secondsUntilRefresh(lookup)).thenReturn(30L);

        mvc.perform(post("/api/players/steam/N0-RU/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").value(false))
                .andExpect(jsonPath("$.player.refreshAvailableInSec").value(30));
    }
}
