package gg.pub.web;

import gg.pub.card.CardService;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.service.PlayerLookupService;
import gg.pub.service.PlayerLookupService.Lookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 플레이어 카드 화면 주소별 동작 */
@WebMvcTest(CardController.class)
class CardControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlayerLookupService lookupService;

    @MockitoBean
    CardService cardService;

    @Test
    void 플랫폼이나_닉네임이_이상하면_첫_화면으로() throws Exception {
        mvc.perform(get("/card").param("platform", "xbox").param("nickname", "abc"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/card").param("platform", "steam").param("nickname", " "))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 꾸미기_화면은_카드_계산을_기다리지_않고_React_껍데기를_바로_연다() throws Exception {
        mvc.perform(get("/card").param("platform", "steam").param("nickname", "sample-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("app"))
                .andExpect(content().string(containsString("<title>sample-01 공유 카드 | 펍지지</title>")))
                .andExpect(content().string(containsString("/app/assets/app.js")));
        // 카드 값은 React 가 /api/card 로 받는다 (CardApiControllerTest)
        verify(lookupService, never()).findWithCard(any(), any());
    }

    private static final String BODY = """
            {"platform":"steam","nickname":"sample-01","choices":{"mic":"o","time":"night"},
             "background":{"map":"erangel","sx":10,"sy":20},
             "png":"data:image/png;base64,iVBORw0KGgo="}
            """;

    private void found() {
        PlayerMatches result = new PlayerMatches("Sample-01", Platform.STEAM, null, List.of());
        when(lookupService.findWithCard(Platform.STEAM, "sample-01"))
                .thenReturn(new Lookup(result, Instant.parse("2026-10-20T12:00:00Z")));
    }

    @Test
    void 공유하면_ID와_공유_주소를_준다() throws Exception {
        found();
        when(cardService.share(eq(Platform.STEAM), eq("Sample-01"), anyMap(), any(), any())).thenReturn("abc123XYZ_-");

        mvc.perform(post("/api/share").contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc123XYZ_-"))
                .andExpect(jsonPath("$.link").value("/s/abc123XYZ_-"));
    }

    @Test
    void 값이_이상하면_400() throws Exception {
        found();
        when(cardService.share(eq(Platform.STEAM), eq("Sample-01"), anyMap(), any(), any()))
                .thenThrow(new IllegalArgumentException("카드 값이 올바르지 않아요: mic"));

        mvc.perform(post("/api/share").contentType("application/json").content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void PNG_가_아니면_400() throws Exception {
        mvc.perform(post("/api/share").contentType("application/json")
                        .content(BODY.replace("data:image/png;base64,", "data:text/html,")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 옛_저장_주소는_없어졌다() throws Exception {
        mvc.perform(post("/api/card").contentType("application/json").content(BODY))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void 공유한_그림을_돌려준다() throws Exception {
        when(cardService.image("abc123XYZ_-")).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        mvc.perform(get("/s/abc123XYZ_-/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void 그림이_없거나_비워졌으면_404() throws Exception {
        when(cardService.image("nothere123")).thenReturn(Optional.empty());

        mvc.perform(get("/s/nothere123/image"))
                .andExpect(status().isNotFound());
    }
}
