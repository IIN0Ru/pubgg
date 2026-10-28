package gg.pub.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.card.CardLockedException;
import gg.pub.card.CardService;
import gg.pub.card.SavedCard;
import gg.pub.domain.Platform;
import gg.pub.domain.ShareCard;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.service.PlayerLookupService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 플레이어 카드.
 *  - GET  /card?platform=steam&nickname=N0-RU   카드 꾸미기 화면 (저장한 카드가 있으면 그 모습으로 시작)
 *  - POST /api/card                              사이트에 저장 (꾸민 값 + 미리보기 PNG)
 *  - GET  /card/image?platform=…&nickname=…      저장한 카드 그림 (디스코드·카톡 링크 미리보기용)
 *
 * 기록 값(KDA·딜·태그)은 저장하지 않고 늘 최신 검색 결과로 채운다. 꾸민 값은 주소 뒤에도 붙어서
 * 저장하지 않은 카드도 링크로 같은 모습을 볼 수 있다.
 */
@Controller
public class CardController {

    /** 옷 색 [값, 이름]. 캐릭터 옷 색이라 사이트 색 변수와 따로 둔다 (player-card.js 도 이 값을 그대로 쓴다) */
    static final List<String[]> TEE_COLORS = List.of(
            new String[]{"f0f0e8", "흰색"},
            new String[]{"2a2a2e", "검정"},
            new String[]{"8c8c92", "회색"},
            new String[]{"c8423a", "빨강"},
            new String[]{"3a64c8", "파랑"},
            new String[]{"3f8a4a", "초록"},
            new String[]{"e0b43a", "노랑"},
            new String[]{"e08aa8", "분홍"});

    private static final String PNG_DATA_URL = "data:image/png;base64,";

    private final PlayerLookupService lookupService;
    private final CardService cardService;
    private final ObjectMapper objectMapper;

    public CardController(PlayerLookupService lookupService, CardService cardService, ObjectMapper objectMapper) {
        this.lookupService = lookupService;
        this.cardService = cardService;
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
            String exact = lookup.result().nickname();
            model.addAttribute("nickname", exact);
            Optional<ShareCard> card = ShareCard.from(lookup.result());
            if (card.isPresent()) {
                model.addAttribute("card", card.get());
                model.addAttribute("cardJson", toJson(card.get()));
                model.addAttribute("savedJson", cardService.find(selected.get(), exact).map(this::toJson).orElse(""));
            }
        } catch (PubgApiException e) {
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "card";
    }

    /**
     * 사이트에 저장.
     *
     * @param png        미리보기 그림 "data:image/png;base64,…"
     * @param token      이 브라우저의 수정 열쇠 (처음이면 null)
     */
    public record SaveRequest(String platform, String nickname, Map<String, String> choices,
                              SavedCard.Background background, String png, String token) {
    }

    @PostMapping("/api/card")
    @ResponseBody
    public ResponseEntity<Map<String, String>> save(@RequestBody SaveRequest req) {
        Optional<Platform> selected = Platform.fromShard(req.platform());
        if (selected.isEmpty() || req.nickname() == null || req.nickname().isBlank()
                || req.png() == null || !req.png().startsWith(PNG_DATA_URL)) {
            return error(HttpStatus.BAD_REQUEST, "저장할 값이 올바르지 않아요.");
        }
        try {
            // 검색된 적 있는 플레이어만 (정확한 닉네임으로 저장)
            String exact = lookupService.find(selected.get(), req.nickname().trim()).result().nickname();
            byte[] png = Base64.getDecoder().decode(req.png().substring(PNG_DATA_URL.length()));
            String token = cardService.save(selected.get(), exact, req.choices(), req.background(), png, req.token());
            String link = UriComponentsBuilder.fromPath("/search")
                    .queryParam("platform", selected.get().getShard())
                    .queryParam("nickname", exact)
                    .encode().build().toUriString();
            return ResponseEntity.ok(Map.of("token", token, "link", link));
        } catch (CardLockedException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (PubgApiException e) {
            return error(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /** 저장한 카드 그림. 링크 미리보기(og:image)가 이 주소를 가리킨다 */
    @GetMapping("/card/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(@RequestParam(required = false) String platform,
                                        @RequestParam(required = false) String nickname) {
        Optional<Platform> selected = Platform.fromShard(platform);
        if (selected.isEmpty() || nickname == null || nickname.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return cardService.image(selected.get(), nickname.trim())
                .map(png -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카드 값을 JSON 으로 못 바꿈", e);
        }
    }
}
