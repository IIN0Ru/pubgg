package gg.pub.web.api;

import gg.pub.domain.Platform;
import gg.pub.service.PlayerLookupService;
import gg.pub.service.PlayerLookupService.Lookup;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전적 화면(React)이 부르는 JSON API. 화면을 그리는 일은 하지 않고, 값만 돌려준다.
 * 실제 일은 기존 Service(PlayerLookupService)가 한다 — HTML 화면(HomeController)과 같은 저장 결과를 쓴다.
 *
 *  GET  /api/players/{platform}/{nickname}          전적 (저장된 결과가 있으면 그대로, 없으면 새로 불러옴)
 *  POST /api/players/{platform}/{nickname}/refresh  전적 갱신 (마지막 갱신 후 2분이 안 지났으면 저장본 그대로)
 *
 * 카드는 뒤에서 계산하므로 처음엔 cardStatus 가 "pending" 이다. 프론트가 같은 GET 을 몇 초마다 다시 부르면
 * (저장된 결과를 읽을 뿐이라 PUBG API 를 부르지 않는다) 끝났을 때 "ready" 로 바뀐다.
 *
 * 오류 → 상태 코드 + { code, message } 는 ApiExceptionHandler 가 이 패키지의 API 모두에 같이 적용한다.
 */
@RestController
@RequestMapping("/api/players")
public class PlayerApiController {

    private final PlayerLookupService lookupService;

    public PlayerApiController(PlayerLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/{platform}/{nickname}")
    public PlayerPageResponse get(@PathVariable String platform, @PathVariable String nickname) {
        Platform selected = ApiParams.platform(platform);
        String name = ApiParams.nickname(nickname);
        return toResponse(lookupService.find(selected, name));
    }

    /**
     * @param refreshed 실제로 새로 불러왔으면 true, 2분이 안 지나 저장본을 그대로 줬으면 false
     * @param player    갱신 뒤의 전적
     */
    public record RefreshResponse(boolean refreshed, PlayerPageResponse player) {
    }

    @PostMapping("/{platform}/{nickname}/refresh")
    public RefreshResponse refresh(@PathVariable String platform, @PathVariable String nickname) {
        Platform selected = ApiParams.platform(platform);
        String name = ApiParams.nickname(nickname);
        boolean refreshed = lookupService.refresh(selected, name);
        return new RefreshResponse(refreshed, toResponse(lookupService.find(selected, name)));
    }

    private PlayerPageResponse toResponse(Lookup lookup) {
        return PlayerPageResponse.from(lookup, lookupService.secondsUntilRefresh(lookup));
    }
}
