package gg.pub.web;

import gg.pub.card.CardLockedException;
import gg.pub.card.CardService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    void 경쟁전_기록이_없으면_카드_없이_안내() throws Exception {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());
        when(lookupService.find(Platform.STEAM, "n0-ru"))
                .thenReturn(new Lookup(result, Instant.parse("2026-10-20T12:00:00Z")));

        mvc.perform(get("/card").param("platform", "steam").param("nickname", "n0-ru"))
                .andExpect(status().isOk())
                .andExpect(view().name("card"))
                .andExpect(model().attribute("nickname", "N0-RU"))   // 정확한 닉네임으로
                .andExpect(model().attributeDoesNotExist("card"));
    }

    @Test
    void 플레이어가_없으면_안내_문구() throws Exception {
        when(lookupService.find(Platform.KAKAO, "nobody")).thenThrow(new PlayerNotFoundException("nobody"));

        mvc.perform(get("/card").param("platform", "kakao").param("nickname", "nobody"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));
    }

    private static final String BODY = """
            {"platform":"steam","nickname":"n0-ru","choices":{"mic":"o"},
             "background":{"map":"erangel","sx":10,"sy":20},
             "png":"data:image/png;base64,iVBORw0KGgo=","token":null}
            """;

    private void found() {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());
        when(lookupService.find(Platform.STEAM, "n0-ru"))
                .thenReturn(new Lookup(result, Instant.parse("2026-10-20T12:00:00Z")));
    }

    @Test
    void 저장하면_열쇠와_전적_링크를_준다() throws Exception {
        found();
        when(cardService.save(eq(Platform.STEAM), eq("N0-RU"), anyMap(), any(), any(), isNull())).thenReturn("key1");

        mvc.perform(post("/api/card").contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("key1"))
                .andExpect(jsonPath("$.link").value("/search?platform=steam&nickname=N0-RU"));
    }

    @Test
    void 다른_브라우저가_먼저_저장한_카드면_403() throws Exception {
        found();
        when(cardService.save(eq(Platform.STEAM), eq("N0-RU"), anyMap(), any(), any(), isNull()))
                .thenThrow(new CardLockedException());

        mvc.perform(post("/api/card").contentType("application/json").content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void PNG_가_아니면_400() throws Exception {
        mvc.perform(post("/api/card").contentType("application/json")
                        .content(BODY.replace("data:image/png;base64,", "data:text/html,")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 저장한_그림이_없으면_404() throws Exception {
        mvc.perform(get("/card/image").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().isNotFound());
    }
}
