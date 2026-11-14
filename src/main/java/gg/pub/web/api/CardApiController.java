package gg.pub.web.api;

import gg.pub.domain.Platform;
import gg.pub.domain.ShareCard;
import gg.pub.service.PlayerLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공유 카드 꾸미기 화면(React, /card)이 부르는 JSON API.
 *
 *  GET /api/card/{platform}/{nickname}   카드에 그릴 기록 값 (ShareCard). 카드 계산이 끝날 때까지 기다렸다가 준다
 *
 * 공유 링크 만들기(POST /api/share)와 미리보기 그림은 web/CardController 에 있다.
 * 오류는 ApiExceptionHandler 가 { code, message } 로 바꾼다 (없는 플레이어 404, 요청 한도 429 …).
 */
@RestController
public class CardApiController {

    private final PlayerLookupService lookupService;

    public CardApiController(PlayerLookupService lookupService) {
        this.lookupService = lookupService;
    }

    /**
     * @param nickname 서버가 찾은 정확한 닉네임 (대소문자)
     * @param card     최근 30경기 안에 경쟁전·일반 경기 기록이 없으면 null
     */
    public record CardPageResponse(String nickname, PlayerPageResponse.PlatformDto platform, ShareCard card) {
    }

    @GetMapping("/api/card/{platform}/{nickname}")
    public CardPageResponse get(@PathVariable String platform, @PathVariable String nickname) {
        Platform selected = ApiParams.platform(platform);
        PlayerLookupService.Lookup lookup = lookupService.findWithCard(selected, ApiParams.nickname(nickname));
        return new CardPageResponse(lookup.result().nickname(),
                new PlayerPageResponse.PlatformDto(selected.getShard(), selected.getLabel()),
                ShareCard.from(lookup.result()).orElse(null));
    }
}
