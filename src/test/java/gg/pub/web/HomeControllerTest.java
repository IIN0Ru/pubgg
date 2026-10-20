package gg.pub.web;

import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.pubgapi.PlayerNotFoundException;
import gg.pub.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 컨트롤러만 띄워서 주소별 동작을 확인한다.
 * PlayerService 는 가짜(MockitoBean)로 바꿔서 실제 PUBG API 를 부르지 않는다.
 */
@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlayerService playerService;

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
        when(playerService.getRecentMatches(Platform.STEAM, "N0-RU")).thenReturn(result);

        mvc.perform(get("/search").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("result", result))
                .andExpect(model().attributeExists("platforms"));   // 상단 검색창용
    }

    @Test
    void 플레이어가_없으면_안내_문구를_보여준다() throws Exception {
        when(playerService.getRecentMatches(Platform.KAKAO, "nobody"))
                .thenThrow(new PlayerNotFoundException("nobody"));

        mvc.perform(get("/search").param("platform", "kakao").param("nickname", "nobody"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attributeExists("errorMessage"));
    }
}
