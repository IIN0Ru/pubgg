package gg.pub.web;

import gg.pub.card.CardService;
import gg.pub.card.SharedCard;
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
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    CardService cardService;   // 공유 카드 (공유 주소 /s/{id} 를 열 때만 쓴다)

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
    void 검색하면_전적을_기다리지_않고_화면_껍데기를_바로_연다() throws Exception {
        mvc.perform(get("/search").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().isOk())
                .andExpect(view().name("app"))
                .andExpect(content().string(containsString("/app/assets/app.js")))
                // React 가 읽는 검색 대상
                .andExpect(content().string(containsString("data-platform=\"steam\"")))
                .andExpect(content().string(containsString("data-nickname=\"N0-RU\"")))
                // 저장된 결과가 없으면 미리보기 제목은 닉네임만
                .andExpect(content().string(containsString("<title>N0-RU | 펍지지</title>")));
        verify(lookupService, never()).find(any(), any());   // PUBG API 는 React 가 /api 로 부른다
    }

    @Test
    void 이미_저장된_전적이_있으면_미리보기_제목과_설명을_채운다() throws Exception {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());
        when(lookupService.findSaved(Platform.STEAM, "N0-RU"))
                .thenReturn(Optional.of(new Lookup(result, Instant.parse("2026-10-20T12:00:00Z"))));

        mvc.perform(get("/search").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("og:description\" content=\"" + result.shareDescription())));
    }

    @Test
    void 닉네임에_따옴표가_있어도_속성이_깨지지_않는다() throws Exception {
        mvc.perform(get("/search").param("platform", "steam").param("nickname", "a\"b<c"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-nickname=\"a\"b"))));
    }

    private static SharedCard share(String id, String nickname) {
        return new SharedCard(id, "steam", nickname, Map.of("mic", "o"),
                new SharedCard.Background("erangel", 10, 20), Instant.parse("2026-11-07T12:00:00Z"));
    }

    private PlayerMatches searchFound() {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());
        when(lookupService.find(Platform.STEAM, "N0-RU"))
                .thenReturn(new Lookup(result, Instant.parse("2026-11-07T12:00:00Z")));
        return result;
    }

    @Test
    void 공유_주소를_열면_전적_화면과_미리보기_그림_주소를_준다() throws Exception {
        PlayerMatches result = searchFound();
        when(cardService.find("abc123XYZ_-")).thenReturn(Optional.of(share("abc123XYZ_-", "N0-RU")));
        when(cardService.hasImage("abc123XYZ_-")).thenReturn(true);

        mvc.perform(get("/s/abc123XYZ_-"))
                .andExpect(status().isOk())
                .andExpect(view().name("app"))
                .andExpect(model().attribute("nickname", "N0-RU"))
                .andExpect(model().attribute("cardImageUrl", "http://localhost/s/abc123XYZ_-/image"))
                // 링크 미리보기 크롤러는 JS 를 안 돌리므로 서버가 그린 <head> 에 있어야 한다
                .andExpect(content().string(containsString("og:image\" content=\"http://localhost/s/abc123XYZ_-/image\"")))
                .andExpect(content().string(containsString("summary_large_image")))
                .andExpect(content().string(containsString("og:title\" content=\"" + result.shareTitle())))
                .andExpect(content().string(containsString("data-nickname=\"N0-RU\"")));
    }

    @Test
    void 그림이_비워진_공유_주소도_전적_화면은_열리고_미리보기_그림만_없다() throws Exception {
        searchFound();
        when(cardService.find("old123456")).thenReturn(Optional.of(share("old123456", "N0-RU")));
        when(cardService.hasImage("old123456")).thenReturn(false);

        mvc.perform(get("/s/old123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("app"))
                .andExpect(model().attributeDoesNotExist("cardImageUrl"))
                .andExpect(content().string(not(containsString("og:image\""))));
    }

    @Test
    void 전적을_못_불러와도_공유_주소_껍데기는_열린다() throws Exception {
        when(cardService.find("down123456")).thenReturn(Optional.of(share("down123456", "N0-RU")));
        when(cardService.hasImage("down123456")).thenReturn(true);
        when(lookupService.find(Platform.STEAM, "N0-RU")).thenThrow(new PlayerNotFoundException("N0-RU"));

        mvc.perform(get("/s/down123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("app"))
                .andExpect(content().string(containsString("<title>N0-RU | 펍지지</title>")))
                .andExpect(content().string(containsString("og:image\"")));   // 그림은 전적과 상관없이 보여준다
    }

    @Test
    void 없는_공유_주소는_첫_화면으로() throws Exception {
        when(cardService.find("nothere123")).thenReturn(Optional.empty());

        mvc.perform(get("/s/nothere123"))
                .andExpect(redirectedUrl("/"));
    }
}
