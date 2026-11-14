package gg.pub.web;

import gg.pub.card.CardService;
import gg.pub.card.ShareRateLimiter;
import gg.pub.card.SharedCard;
import gg.pub.domain.Platform;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.service.PlayerLookupService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * 공유 카드(B카드).
 *  - GET  /card?platform=steam&nickname=Sample-01   공유 카드 꾸미기 화면 껍데기 (React 가 그린다)
 *  - POST /api/share                             공유 카드를 만들고 공유 주소(/s/{id})를 돌려준다
 *  - GET  /s/{id}/image                          공유한 카드 그림 (디스코드·카톡 링크 미리보기용)
 *
 * 꾸미기 화면에 쓸 카드 값은 web/api/CardApiController 가 JSON 으로 준다.
 * 공유 주소 /s/{id} 자체는 HomeController 가 전적 화면(A카드)으로 보여준다.
 * 기록 값(KDA·딜·전투 태그)은 저장하지 않고 늘 최신 검색 결과로 채운다. 꾸민 값은 주소 뒤에도 붙어서
 * 새로고침해도 같은 모습으로 이어진다.
 */
@Controller
public class CardController {

    private static final String PNG_DATA_URL = "data:image/png;base64,";

    /** 같은 접속 주소가 10분에 10번까지만 공유 카드를 만든다 */
    private final ShareRateLimiter rateLimiter = new ShareRateLimiter(Clock.systemUTC(), 10, Duration.ofMinutes(10));

    private final PlayerLookupService lookupService;
    private final CardService cardService;

    public CardController(PlayerLookupService lookupService, CardService cardService) {
        this.lookupService = lookupService;
        this.cardService = cardService;
    }

    /**
     * 꾸미기 화면 껍데기. 카드 값은 React 가 GET /api/card/{platform}/{nickname} (CardApiController) 으로 받는다.
     * 주소가 이상하면 첫 화면으로.
     */
    @GetMapping("/card")
    public String card(@RequestParam(required = false) String platform,
                       @RequestParam(required = false) String nickname,
                       Model model) {
        Optional<Platform> selected = Platform.fromShard(platform);
        String name = nickname == null ? "" : nickname.trim();
        if (selected.isEmpty() || name.isEmpty()) {
            return "redirect:/";
        }
        return Shell.fill(model, name + " 공유 카드 | 펍지지", name + " 공유 카드", Shell.DEFAULT_DESCRIPTION);
    }

    /**
     * 공유 카드 만들기.
     *
     * @param png 미리보기 그림 "data:image/png;base64,…"
     */
    public record ShareRequest(String platform, String nickname, Map<String, String> choices,
                               SharedCard.Background background, String png) {
    }

    @PostMapping("/api/share")
    @ResponseBody
    public ResponseEntity<Map<String, String>> share(@RequestBody ShareRequest req, HttpServletRequest http) {
        Optional<Platform> selected = Platform.fromShard(req.platform());
        if (selected.isEmpty() || req.nickname() == null || req.nickname().isBlank()
                || req.png() == null || !req.png().startsWith(PNG_DATA_URL)) {
            return error(HttpStatus.BAD_REQUEST, "공유할 값이 올바르지 않아요.");
        }
        if (!rateLimiter.tryAcquire(http.getRemoteAddr())) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "공유 링크를 너무 많이 만들었어요. 잠시 뒤에 다시 해 주세요.");
        }
        try {
            // 검색된 적 있는 플레이어만 (정확한 닉네임으로 저장)
            String exact = lookupService.findWithCard(selected.get(), req.nickname().trim()).result().nickname();
            byte[] png = Base64.getDecoder().decode(req.png().substring(PNG_DATA_URL.length()));
            String id = cardService.share(selected.get(), exact, req.choices(), req.background(), png);
            return ResponseEntity.ok(Map.of("id", id, "link", "/s/" + id));
        } catch (IllegalArgumentException e) {   // 값 검사 실패, Base64 가 아닌 그림
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (PubgApiException e) {
            return error(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /** 공유한 카드 그림. 링크 미리보기(og:image)가 이 주소를 가리킨다 */
    @GetMapping("/s/{id}/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        return cardService.image(id)
                .map(png -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
