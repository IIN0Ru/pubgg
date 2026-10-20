package gg.pub.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    MockMvc mvc;

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
    void 정상_검색이면_검색_화면을_보여준다() throws Exception {
        mvc.perform(get("/search").param("platform", "kakao").param("nickname", "치킨사냥꾼"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("platform", Platform.KAKAO))
                .andExpect(model().attribute("nickname", "치킨사냥꾼"));
    }
}
