package gg.pub.web;

import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.replay.MapInfo;
import gg.pub.domain.replay.Replay;
import gg.pub.service.ReplayService;
import gg.pub.service.ReplayUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 리플레이 화면·데이터 주소 확인 (ReplayService 는 가짜) */
@WebMvcTest(ReplayController.class)
class ReplayControllerTest {

    private static final String MATCH = "0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0";
    private static final String PLAYER = "account.abc123";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReplayService replayService;

    private static MatchRecord record() {
        MatchPlayer me = new MatchPlayer("N0-RU", PLAYER, 3, 4, 1, 512, 2, 1800, 1, 120, 0, "alive");
        return new MatchRecord(MATCH, Instant.parse("2026-10-19T14:12:30Z"), "squad-fpp", "Baltic_Main",
                "competitive", 16, "https://telemetry.example/x", me, List.of());
    }

    @Test
    void 리플레이_화면이_열린다() throws Exception {
        when(replayService.record(Platform.STEAM, MATCH, PLAYER)).thenReturn(record());

        mvc.perform(get("/replay").param("platform", "steam").param("match", MATCH)
                        .param("player", PLAYER).param("nickname", "N0-RU"))
                .andExpect(status().isOk())
                .andExpect(view().name("replay"))
                .andExpect(model().attributeExists("record"));
    }

    @Test
    void 주소가_이상하면_첫_화면으로() throws Exception {
        mvc.perform(get("/replay").param("platform", "steam").param("match", "../../x").param("player", PLAYER))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/replay").param("platform", "steam").param("match", MATCH).param("player", "nobody"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 일반전이면_안내_문구() throws Exception {
        when(replayService.record(Platform.STEAM, MATCH, PLAYER))
                .thenThrow(new ReplayUnavailableException("리플레이는 경쟁전 경기만 볼 수 있어요."));

        mvc.perform(get("/replay").param("platform", "steam").param("match", MATCH).param("player", PLAYER))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "리플레이는 경쟁전 경기만 볼 수 있어요."));
    }

    @Test
    void 데이터는_JSON_으로() throws Exception {
        Replay replay = new Replay(MATCH, MapInfo.of("Baltic_Main").orElseThrow(), 600, PLAYER, 1,
                List.of(new Replay.Track(PLAYER, "N0-RU", 1, new int[]{10, 20}, new int[]{100, 110}, new int[]{200, 210}, new int[]{0, 1}, null)),
                List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of());
        when(replayService.replay(Platform.STEAM, MATCH, PLAYER)).thenReturn(replay);

        mvc.perform(get("/api/replay").param("platform", "steam").param("match", MATCH).param("player", PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.map.tiles").value("/images/maps/tiles/erangel"))
                .andExpect(jsonPath("$.map.levels").value(5))
                .andExpect(jsonPath("$.players[0].t[1]").value(20));
    }

    @Test
    void 데이터를_못_만들면_404_와_안내_문구() throws Exception {
        when(replayService.replay(Platform.STEAM, MATCH, PLAYER))
                .thenThrow(new ReplayUnavailableException("사녹 맵은 아직 리플레이를 지원하지 않아요."));

        mvc.perform(get("/api/replay").param("platform", "steam").param("match", MATCH).param("player", PLAYER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사녹 맵은 아직 리플레이를 지원하지 않아요."));
    }
}
