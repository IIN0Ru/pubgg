package gg.pub.service;

import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 검색 결과를 저장해 두고, "전적 갱신"을 눌렀을 때만 새로 불러온다. (OP.GG 방식)
 *
 *  - 처음 검색: PUBG API 로 불러와서 저장
 *  - 다시 검색: 저장된 결과를 바로 보여줌 (API 를 부르지 않음)
 *  - 갱신: 마지막 갱신 후 2분이 지났으면 새로 불러옴. 2분 안이면 저장된 결과 그대로
 *
 * 이유: PUBG API 는 플레이어 조회가 1분에 10번으로 제한되고, 한 번 검색에 경기 수십 개를 불러와서 느리다.
 *
 * 주의: 지금은 서버 메모리에 저장해서 서버를 다시 켜면 비워진다. 배포할 때 다시 검토.
 */
@Service
public class PlayerLookupService {

    /** 갱신 후 다시 갱신할 수 있을 때까지 기다리는 시간 */
    static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(2);

    private final PlayerService playerService;
    private final Clock clock;
    private final Map<String, Lookup> store = new ConcurrentHashMap<>();

    public PlayerLookupService(PlayerService playerService, Clock clock) {
        this.playerService = playerService;
        this.clock = clock;
    }

    /**
     * 검색 결과 + 마지막 갱신 시각.
     *
     * @param result    검색 결과
     * @param updatedAt 마지막으로 PUBG API 에서 불러온 시각
     */
    public record Lookup(PlayerMatches result, Instant updatedAt) {
    }

    /** 저장된 결과가 있으면 그걸, 없으면 새로 불러온다 */
    public Lookup find(Platform platform, String nickname) {
        Lookup saved = store.get(key(platform, nickname));
        return saved != null ? saved : fetch(platform, nickname);
    }

    /**
     * 새로 불러온다. 단, 마지막 갱신 후 2분이 안 지났으면 저장된 결과를 그대로 돌려준다.
     *
     * @return 실제로 새로 불러왔으면 true
     */
    public boolean refresh(Platform platform, String nickname) {
        Lookup saved = store.get(key(platform, nickname));
        if (saved != null && secondsUntilRefresh(saved) > 0) {
            return false;
        }
        fetch(platform, nickname);
        return true;
    }

    /** 다시 갱신할 수 있을 때까지 남은 초. 0이면 지금 갱신 가능 */
    public long secondsUntilRefresh(Lookup lookup) {
        Instant availableAt = lookup.updatedAt().plus(REFRESH_COOLDOWN);
        long seconds = Duration.between(clock.instant(), availableAt).toSeconds();
        return Math.max(0, seconds);
    }

    /** "방금 전", "3분 전", "2시간 전", "1일 전" */
    public String updatedAgoText(Lookup lookup) {
        Duration ago = Duration.between(lookup.updatedAt(), clock.instant());
        if (ago.toMinutes() < 1) {
            return "방금 전";
        }
        if (ago.toHours() < 1) {
            return ago.toMinutes() + "분 전";
        }
        if (ago.toDays() < 1) {
            return ago.toHours() + "시간 전";
        }
        return ago.toDays() + "일 전";
    }

    private Lookup fetch(Platform platform, String nickname) {
        PlayerMatches result = playerService.getRecentMatches(platform, nickname);
        Lookup lookup = new Lookup(result, clock.instant());
        store.put(key(platform, nickname), lookup);
        // PUBG 에 등록된 정확한 닉네임으로도 찾을 수 있게 같이 저장 (대소문자가 다르게 검색될 수 있어서)
        store.put(key(platform, result.nickname()), lookup);
        return lookup;
    }

    private static String key(Platform platform, String nickname) {
        return platform.getShard() + ":" + nickname.toLowerCase(Locale.ROOT);
    }
}
