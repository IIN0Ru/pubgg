package gg.pub.web.api;

import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.pubgapi.PlayerNotFoundException;
import gg.pub.service.PlayerLookupService;
import gg.pub.service.PlayerLookupService.Lookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공유 카드 꾸미기 화면(React)이 부르는 카드 값 API. 프론트 타입은 frontend/src/api/types.ts 의 CardPage */
@WebMvcTest(CardApiController.class)
class CardApiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlayerLookupService lookupService;

    @Test
    void 정확한_닉네임과_플랫폼을_주고_기록이_없으면_카드는_null() throws Exception {
        PlayerMatches result = new PlayerMatches("Sample-01", Platform.STEAM, null, List.of());
        when(lookupService.findWithCard(Platform.STEAM, "sample-01"))
                .thenReturn(new Lookup(result, Instant.parse("2026-10-20T12:00:00Z")));

        mvc.perform(get("/api/card/steam/sample-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Sample-01"))   // 대소문자를 서버가 찾은 대로
                .andExpect(jsonPath("$.platform.shard").value("steam"))
                .andExpect(jsonPath("$.platform.label").value("스팀"))
                .andExpect(jsonPath("$.card").value(nullValue()));
    }

    @Test
    void 없는_플레이어는_404_PLAYER_NOT_FOUND() throws Exception {
        when(lookupService.findWithCard(Platform.KAKAO, "nobody")).thenThrow(new PlayerNotFoundException("nobody"));

        mvc.perform(get("/api/card/kakao/nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 모르는_플랫폼은_400() throws Exception {
        mvc.perform(get("/api/card/xbox/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
