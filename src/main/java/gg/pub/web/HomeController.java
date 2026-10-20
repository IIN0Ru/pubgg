package gg.pub.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * 첫 화면(히어로)과 닉네임 검색 요청을 처리한다.
 * 지금은 화면만 연결해 두었고, 실제 PUBG API 호출은 다음 단계에서 붙인다.
 */
@Controller
public class HomeController {

    /** 첫 화면. templates/index.html 을 보여준다. */
    @GetMapping("/")
    public String home(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("platforms", Platform.values());
        model.addAttribute("error", error);
        return "index";
    }

    /**
     * 검색 버튼을 누르면 /search?platform=steam&nickname=... 으로 들어온다.
     * 값이 비었거나 플랫폼이 이상하면 첫 화면으로 돌려보낸다.
     */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String platform,
                         @RequestParam(required = false) String nickname,
                         Model model) {

        Optional<Platform> selected = Platform.fromShard(platform);
        String name = nickname == null ? "" : nickname.trim();

        if (selected.isEmpty()) {
            return "redirect:/?error=platform";
        }
        if (name.isEmpty()) {
            return "redirect:/?error=nickname";
        }

        model.addAttribute("platform", selected.get());
        model.addAttribute("nickname", name);
        return "search";
    }
}
