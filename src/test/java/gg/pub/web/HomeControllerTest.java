package gg.pub.web;

import gg.pub.card.CardService;
import gg.pub.domain.Farming;
import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 컨트롤러만 띄워서 주소별 동작을 확인한다.
 * PlayerLookupService 는 가짜(MockitoBean)로 바꿔서 실제 PUBG API 를 부르지 않는다.
 */
@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlayerLookupService lookupService;

    @MockitoBean
    CardService cardService;   // 저장한 플레이어 카드 (없으면 빈 값)

    @Test
    void 첫_화면이_열린다() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void 닉네임이_비면_첫_화면으로_돌아간다() throws Exception {
        mvc.perform(get("/search").param("platform", "steam").param("nickname", "  "))
                .andExpect(redirectedUrl("/?error=nickname"));
    }

    @Test
    void 모르는_플랫폼이면_첫_화면으로_돌아간다() throws Exception {
        mvc.perform(get("/search").param("platform", "xbox").param("nickname", "abc"))
                .andExpect(redirectedUrl("/?error=platform"));
    }

    @Test
    void 정상_검색이면_경기_목록을_보여준다() throws Exception {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());
        Lookup lookup = new Lookup(result, Instant.parse("2026-10-20T12:00:00Z"));
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);
        when(lookupService.updatedAgoText(lookup)).thenReturn("3분 전");
        when(lookupService.secondsUntilRefresh(lookup)).thenReturn(0L);

        mvc.perform(get("/search").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("result", result))
                .andExpect(model().attributeExists("platforms"))   // 상단 검색창용
                .andExpect(model().attribute("updatedAgo", "3분 전"));
    }

    @Test
    void 플레이어가_없으면_안내_문구를_보여준다() throws Exception {
        when(lookupService.find(Platform.KAKAO, "nobody"))
                .thenThrow(new PlayerNotFoundException("nobody"));

        mvc.perform(get("/search").param("platform", "kakao").param("nickname", "nobody"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void 갱신하면_같은_검색_결과로_돌아간다() throws Exception {
        when(lookupService.refresh(Platform.STEAM, "N0-RU")).thenReturn(true);

        mvc.perform(post("/refresh").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/search?platform=steam&nickname=N0-RU"));
    }

    @Test
    void 대기_시간_안에_갱신하면_안내_문구를_남긴다() throws Exception {
        when(lookupService.refresh(Platform.STEAM, "N0-RU")).thenReturn(false);

        mvc.perform(post("/refresh").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(flash().attributeExists("refreshMessage"));
    }

    @Test
    void 경쟁전_카드가_있으면_플레이어_카드를_그릴_값을_넘긴다() throws Exception {
        MatchPlayer me = new MatchPlayer("N0-RU", "me", 2, 0, 0, 300, 5, 0, 1, 0, 0, "alive");
        MatchRecord game = new MatchRecord("m", Instant.parse("2026-10-19T13:10:00Z"), "squad-fpp", "Baltic_Main",
                "competitive", 16, null, me, List.of());
        PlayCard card = new PlayCard(1, 2, 0, 1, 300, "FPP", List.of(), 1.0, 1,
                null, List.of(), null, List.of(), new Farming(null, null, 0, 0, 0, 0, 0, 0));
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, card, List.of(game));
        Lookup lookup = new Lookup(result, Instant.parse("2026-10-20T12:00:00Z"));
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenReturn(lookup);
        when(lookupService.updatedAgoText(lookup)).thenReturn("3분 전");

        mvc.perform(get("/search").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("cardJson"))
                .andExpect(model().attribute("savedJson", ""))              // 저장한 적 없음 → 기본 옷 + 랜덤 배경
                .andExpect(model().attributeDoesNotExist("cardImageUrl"));  // 미리보기 그림도 없음
    }
}
