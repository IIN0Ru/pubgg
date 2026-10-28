package gg.pub.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.Platform;
import gg.pub.domain.ShareCard;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.service.PlayerLookupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * 공유용 플레이어 카드 화면.
 *  GET /card?platform=steam&nickname=N0-RU[&mic=o&aim=chicken&find=squad&role=igl&tee=ffffff]
 *
 * 기록은 검색 결과(저장된 것)를 그대로 쓰고, 카드 그리기·꾸미기·GIF/PNG 저장은 화면(player-card.js)이 한다.
 * 사용자가 고른 성향 태그와 반팔 색은 DB 없이 주소 뒤에 붙어서, 링크를 받은 사람도 같은 카드를 본다.
 */
@Controller
public class CardController {

    /** 반팔티 색 [값, 이름]. 캐릭터 옷 색이라 사이트 색 변수와 따로 둔다 (player-card.js 도 이 값을 그대로 쓴다) */
    static final List<String[]> TEE_COLORS = List.of(
            new String[]{"f0f0e8", "흰색"},
            new String[]{"2a2a2e", "검정"},
            new String[]{"8c8c92", "회색"},
            new String[]{"c8423a", "빨강"},
            new String[]{"3a64c8", "파랑"},
            new String[]{"3f8a4a", "초록"},
            new String[]{"e0b43a", "노랑"},
            new String[]{"e08aa8", "분홍"});

    private final PlayerLookupService lookupService;
    private final ObjectMapper objectMapper;

    public CardController(PlayerLookupService lookupService, ObjectMapper objectMapper) {
        this.lookupService = lookupService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/card")
    public String card(@RequestParam(required = false) String platform,
                       @RequestParam(required = false) String nickname,
                       Model model) {
        Optional<Platform> selected = Platform.fromShard(platform);
        String name = nickname == null ? "" : nickname.trim();
        if (selected.isEmpty() || name.isEmpty()) {
            return "redirect:/";
        }

        model.addAttribute("platforms", Platform.values());   // 상단 검색창용
        model.addAttribute("platform", selected.get());
        model.addAttribute("nickname", name);
        model.addAttribute("teeColors", TEE_COLORS);
        try {
            PlayerLookupService.Lookup lookup = lookupService.find(selected.get(), name);
            model.addAttribute("nickname", lookup.result().nickname());
            Optional<ShareCard> card = ShareCard.from(lookup.result());
            if (card.isPresent()) {
                model.addAttribute("card", card.get());
                model.addAttribute("cardJson", toJson(card.get()));
            }
        } catch (PubgApiException e) {
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "card";
    }

    private String toJson(ShareCard card) {
        try {
            return objectMapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카드 값을 JSON 으로 못 바꿈", e);
        }
    }
}
