package gg.pub.web;

import gg.pub.domain.Platform;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.service.PlayerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * 첫 화면(히어로)과 닉네임 검색 요청을 처리한다. (홀 직원 역할)
 * 직접 계산하지 않고, 실제 일은 PlayerService 에 맡긴다.
 */
@Controller
public class HomeController {

    private final PlayerService playerService;

    public HomeController(PlayerService playerService) {
        this.playerService = playerService;
    }

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
     * 정상이면 최근 경기 목록을 가져와서 search.html 에 보여준다.
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

        model.addAttribute("platforms", Platform.values());   // 상단 검색창용
        model.addAttribute("platform", selected.get());
        model.addAttribute("nickname", name);

        try {
            model.addAttribute("result", playerService.getRecentMatches(selected.get(), name));
        } catch (PubgApiException e) {
            // 플레이어 없음, 키 문제, 요청 초과 등: 화면에 안내 문구로 보여준다
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "search";
    }
}
