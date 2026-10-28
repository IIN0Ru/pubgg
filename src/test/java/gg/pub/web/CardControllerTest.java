package gg.pub.web;

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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 플레이어 카드 화면 주소별 동작 */
@WebMvcTest(CardController.class)
class CardControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlayerLookupService lookupService;

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
}
