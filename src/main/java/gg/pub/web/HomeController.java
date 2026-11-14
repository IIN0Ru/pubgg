package gg.pub.web;

import gg.pub.card.CardService;
import gg.pub.card.SharedCard;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.service.PlayerLookupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;

/**
 * 첫 화면(/)과 전적 화면(/search, /s/{id})의 React 껍데기를 연다. (홀 직원 역할)
 * 화면은 React 가 그리고, 여기서는 주소 검사와 링크 미리보기용 <head> 만 채운다 (Shell).
 */
@Controller
public class HomeController {

    private final PlayerLookupService lookupService;
    private final CardService cardService;

    public HomeController(PlayerLookupService lookupService, CardService cardService) {
        this.lookupService = lookupService;
        this.cardService = cardService;
    }

    /** 첫 화면. 주소의 ?error= (아래 /search 가 돌려보낼 때 붙임) 안내는 React 가 읽는다 */
    @GetMapping("/")
    public String home(Model model) {
        return Shell.fill(model, Shell.SITE_TITLE, Shell.SITE_TITLE, Shell.HOME_DESCRIPTION, Shell.HOME_OG_DESCRIPTION);
    }

    /**
     * 검색 버튼을 누르면 /search?platform=steam&nickname=... 으로 들어온다.
     * 값이 비었거나 플랫폼이 이상하면 첫 화면으로 돌려보낸다.
     *
     * 전적은 서버가 그리지 않는다. 화면 껍데기(app.html)만 바로 주면 React 가 /api/players/... 로 전적을 불러와 그린다.
     * (화면 안에서 검색하면 이 주소를 거치지 않고 React 가 바로 넘어간다. 여기는 처음 열 때·새로고침할 때만)
     * 그래서 처음 검색해도 껍데기가 바로 뜨고, 오류(없는 플레이어·요청 한도 등)도 React 가 안내한다.
     * 링크 미리보기용 <head> 는 이미 저장된 결과가 있을 때만 채운다 (없으면 닉네임만).
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
        Shell.target(model, selected.get(), name);
        return fillPlayer(model, name, lookupService.findSaved(selected.get(), name).map(l -> l.result()).orElse(null));
    }

    /**
     * 공유 주소 /s/{id}. 열면 그 플레이어의 전적 화면(A카드)이 나온다 — 주소는 그대로 둔다.
     * 디스코드·카톡 같은 링크 미리보기는 JS 를 실행하지 않고 이 주소의 <head> 만 읽는다.
     * 그래서 여기서는 저장된 결과가 없어도 전적을 불러와서(없으면 새로) 제목·설명을 채우고, 공유한 B카드 그림(og:image)을 넣는다.
     * 없는 ID 이거나 지워졌으면 첫 화면으로 보낸다.
     */
    @GetMapping("/s/{id}")
    public String shared(@PathVariable String id, Model model) {
        Optional<SharedCard> share = cardService.find(id);
        Optional<Platform> platform = share.flatMap(s -> Platform.fromShard(s.platform()));
        if (platform.isEmpty()) {
            return "redirect:/";
        }
        String name = share.get().nickname();
        Shell.target(model, platform.get(), name);
        PlayerMatches result = null;
        try {
            result = lookupService.find(platform.get(), name).result();
        } catch (PubgApiException e) {
            // 전적을 못 가져와도 껍데기는 연다. 안내는 React 가 한다 (미리보기 제목만 닉네임으로)
        }
        if (cardService.hasImage(id)) {
            // 디스코드·카톡은 미리보기 그림 주소를 전체 주소(https://…)로 받아야 한다
            model.addAttribute("cardImageUrl", ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/s/{id}/image")
                    .buildAndExpand(id)
                    .toUriString());
        }
        return fillPlayer(model, name, result);
    }

    /** 전적 화면 <head>: 저장된 결과가 있으면 미리보기 제목·설명을 채우고, 없으면 닉네임만 */
    private static String fillPlayer(Model model, String name, PlayerMatches result) {
        if (result == null) {
            return Shell.fill(model, name + " | 펍지지", name, Shell.DEFAULT_DESCRIPTION);
        }
        return Shell.fill(model, result.shareTitle() + " | 펍지지", result.shareTitle(), result.shareDescription());
    }
}
