package gg.pub.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AppShellController.class)
class AppShellControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 새_화면_껍데기가_열리고_React_번들을_불러온다() throws Exception {
        mvc.perform(get("/next/search").param("platform", "steam").param("nickname", "N0-RU"))
                .andExpect(status().isOk())
                .andExpect(view().name("app"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/app/assets/app.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"root\"")));
    }
}
