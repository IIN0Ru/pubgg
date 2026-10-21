package gg.pub.service;

import gg.pub.domain.Platform;
import gg.pub.domain.SeasonRank;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.pubgapi.dto.RankedStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이번 시즌 경쟁전 기록(티어, RP, 시즌 KDA·딜량)을 가져온다.
 *
 *  - 지금 시즌 ID 는 플랫폼마다 6시간 저장해 둔다 (시즌은 두 달쯤에 한 번 바뀌어서 매번 물어볼 필요가 없음)
 *  - 경쟁전 기록은 squad(TPP), squad-fpp 두 모드로 나뉘어 오는데, 판 수가 더 많은 모드를 보여준다
 *  - 여기서 실패해도 검색 결과 전체가 깨지면 안 되니, 오류가 나면 빈 값을 돌려준다 (카드에 티어만 안 나옴)
 */
@Service
public class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    static final Duration SEASON_CACHE = Duration.ofHours(6);
    static final List<String> RANKED_MODES = List.of("squad-fpp", "squad");

    private final PubgApiClient client;
    private final Clock clock;
    private final Map<Platform, CachedSeason> seasons = new ConcurrentHashMap<>();

    public SeasonService(PubgApiClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    private record CachedSeason(String id, Instant fetchedAt) {
    }

    /** 이번 시즌 경쟁전 기록. 시즌을 못 찾았거나, 한 판도 안 했거나, 오류가 나면 빈 값 */
    public Optional<SeasonRank> currentRank(Platform platform, String accountId) {
        try {
            Optional<String> seasonId = currentSeasonId(platform);
            if (seasonId.isEmpty()) {
                return Optional.empty();
            }
            return pick(client.getRankedStats(platform, accountId, seasonId.get()));
        } catch (PubgApiException e) {
            log.warn("시즌 기록을 못 받아서 티어 없이 보여줌: {}", e.getMessage());
            return Optional.empty();
        }
    }

    Optional<String> currentSeasonId(Platform platform) {
        CachedSeason cached = seasons.get(platform);
        Instant now = clock.instant();
        if (cached != null && cached.fetchedAt().plus(SEASON_CACHE).isAfter(now)) {
            return Optional.of(cached.id());
        }
        Optional<String> id = client.getSeasons(platform).currentSeasonId();
        id.ifPresent(v -> seasons.put(platform, new CachedSeason(v, now)));
        return id;
    }

    /** 경쟁전 모드 중 판 수가 가장 많은 모드 하나 */
    static Optional<SeasonRank> pick(RankedStatsResponse response) {
        return RANKED_MODES.stream()
                .filter(response.modes()::containsKey)
                .map(mode -> toRank(mode, response.modes().get(mode)))
                .filter(r -> r.rounds() > 0)
                .max(Comparator.comparingInt(SeasonRank::rounds));
    }

    /**
     * 시즌 KDA = (킬 + 어시스트) ÷ 1등 못 한 판 수. 플레이 카드 KDA 와 같은 기준이라 둘을 비교할 수 있다.
     * API 의 kda 값은 실제 응답에서 0 으로 오는 경우가 있어서 쓰지 않는다 (2026-10 실제 경기로 확인).
     * 한 번도 안 진 시즌이면 (킬 + 어시스트) 그대로.
     */
    static double kda(RankedStatsResponse.ModeStats s) {
        int ka = s.kills() + s.assists();
        int lost = s.roundsPlayed() - s.wins();
        return lost <= 0 ? ka : (double) ka / lost;
    }

    private static SeasonRank toRank(String mode, RankedStatsResponse.ModeStats s) {
        RankedStatsResponse.Tier tier = s.currentTier();
        return new SeasonRank(
                mode,
                tier != null ? tier.tier() : null,
                tier != null ? tier.subTier() : null,
                s.currentRankPoint(),
                s.roundsPlayed(),
                kda(s),
                s.roundsPlayed() == 0 ? 0 : (int) Math.round(s.damageDealt() / s.roundsPlayed()),
                s.avgRank(),
                (int) Math.round(s.top10Ratio() * 100));
    }
}
