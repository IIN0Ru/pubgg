package gg.pub.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.card.CardService;
import gg.pub.domain.Platform;
import gg.pub.domain.ShareCard;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.service.PlayerLookupService;
import gg.pub.service.PlayerLookupService.Lookup;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;

/**
 * 첫 화면(히어로), 닉네임 검색, 전적 갱신을 처리한다. (홀 직원 역할)
 * 직접 계산하지 않고, 실제 일은 Service 에 맡긴다.
 */
@Controller
public class HomeController {

    private final PlayerLookupService lookupService;
    private final CardService cardService;
    private final ObjectMapper objectMapper;

    public HomeController(PlayerLookupService lookupService, CardService cardService, ObjectMapper objectMapper) {
        this.lookupService = lookupService;
        this.cardService = cardService;
        this.objectMapper = objectMapper;
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
     * 저장된 결과가 있으면 그걸 보여주고, 없으면 새로 불러온다.
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
            Lookup lookup = lookupService.find(selected.get(), name);
            model.addAttribute("result", lookup.result());
            model.addAttribute("updatedAgo", lookupService.updatedAgoText(lookup));
            model.addAttribute("refreshWait", lookupService.secondsUntilRefresh(lookup));
            addSavedCard(selected.get(), lookup, model);
        } catch (PubgApiException e) {
            // 플레이어 없음, 키 문제, 요청 초과 등: 화면에 안내 문구로 보여준다
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "search";
    }

    /**
     * 왼쪽 위 플레이어 카드 (경쟁전 기록이 있으면 늘). 숫자는 최신 검색 결과(ShareCard),
     * 꾸민 모습은 사이트에 저장한 것(SavedCard) — 저장한 적 없으면 빈 값(화면이 기본 옷 + 랜덤 배경으로 그림).
     * 저장한 미리보기 그림이 있으면 링크 미리보기(og:image)에 쓴다.
     */
    private void addSavedCard(Platform platform, Lookup lookup, Model model) {
        String exact = lookup.result().nickname();
        Optional<ShareCard> card = ShareCard.from(lookup.result());
        if (card.isEmpty()) {
            return;
        }
        model.addAttribute("cardJson", toJson(card.get()));
        model.addAttribute("savedJson", cardService.find(platform, exact).map(this::toJson).orElse(""));
        if (cardService.image(platform, exact).isPresent()) {
            // 디스코드·카톡은 미리보기 그림 주소를 전체 주소(https://…)로 받아야 한다
            model.addAttribute("cardImageUrl", ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/card/image")
                    .queryParam("platform", platform.getShard())
                    .queryParam("nickname", exact)
                    .encode().toUriString());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카드 값을 JSON 으로 못 바꿈", e);
        }
    }

    /**
     * "전적 갱신" 버튼. 새로 불러온 뒤 같은 검색 결과 페이지로 돌아간다.
     * (POST 뒤에 바로 화면을 그리지 않고 다시 GET 으로 보내야, 새로고침해도 갱신이 반복되지 않는다)
     */
    @PostMapping("/refresh")
    public String refresh(@RequestParam String platform,
                          @RequestParam String nickname,
                          RedirectAttributes redirect) {

        Optional<Platform> selected = Platform.fromShard(platform);
        String name = nickname.trim();
        if (selected.isEmpty() || name.isEmpty()) {
            return "redirect:/";
        }

        try {
            if (!lookupService.refresh(selected.get(), name)) {
                redirect.addFlashAttribute("refreshMessage", "방금 갱신했어요. 잠시 뒤에 다시 갱신할 수 있어요.");
            }
        } catch (PubgApiException e) {
            redirect.addFlashAttribute("refreshMessage", e.getMessage());
        }

        // addAttribute 로 넣으면 Spring 이 주소 뒤에 ?platform=...&nickname=... 을 (한글도 알맞게 바꿔서) 붙여준다
        redirect.addAttribute("platform", selected.get().getShard());
        redirect.addAttribute("nickname", name);
        return "redirect:/search";
    }
}
