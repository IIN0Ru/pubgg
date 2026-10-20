package gg.pub.service;

import gg.pub.config.PubgApiProperties;
import gg.pub.domain.GameLabels;
import gg.pub.domain.MatchSummary;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
import gg.pub.domain.PlayerMatches;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.dto.MatchResponse;
import gg.pub.pubgapi.dto.PlayerResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 플레이어 검색 결과를 만든다. (주방장 역할)
 *
 * 순서:
 *  1. Client 로 닉네임 → 플레이어 정보(계정 ID, 최근 경기 ID 목록)를 받는다
 *  2. 최신 경기부터 하나씩 받으면서
 *     - 최근 경기 목록용으로 10경기 (모든 모드)
 *     - 플레이 카드용으로 경쟁전 10경기
 *     를 모은다. 둘 다 채워지면 멈춘다.
 *  3. 경쟁전 경기로 플레이 카드를 계산한다 (PlayCardService)
 */
@Service
public class PlayerService {

    /** 경쟁전 10경기를 찾느라 경기를 너무 많이 받지 않도록 정한 상한 */
    static final int MAX_MATCHES_TO_SCAN = 40;

    private final PubgApiClient client;
    private final PlayCardService playCardService;
    private final int recentMatchLimit;

    public PlayerService(PubgApiClient client, PlayCardService playCardService, PubgApiProperties properties) {
        this.client = client;
        this.playCardService = playCardService;
        this.recentMatchLimit = properties.recentMatchLimit() > 0 ? properties.recentMatchLimit() : 10;
    }

    public PlayerMatches getRecentMatches(Platform platform, String nickname) {
        PlayerResponse.Player player = client.findPlayer(platform, nickname);
        String accountId = player.id();

        List<MatchSummary> recent = new ArrayList<>();
        List<MatchResponse> competitive = new ArrayList<>();

        for (String matchId : player.matchIds().stream().limit(MAX_MATCHES_TO_SCAN).toList()) {
            MatchResponse match = client.getMatch(platform, matchId);
            Optional<MatchSummary> summary = toSummary(match, accountId);
            if (summary.isEmpty()) {
                continue;   // 내 기록이 없는 경기는 건너뛴다
            }

            if (recent.size() < recentMatchLimit) {
                recent.add(summary.get());
            }
            if (match.isCompetitive() && competitive.size() < recentMatchLimit) {
                competitive.add(match);
            }
            if (recent.size() >= recentMatchLimit && competitive.size() >= recentMatchLimit) {
                break;
            }
        }

        PlayCard card = playCardService.build(competitive, accountId).orElse(null);
        String exactName = player.attributes() != null ? player.attributes().name() : nickname;
        return new PlayerMatches(exactName, platform, card, recent);
    }

    /** 경기 하나를 목록 한 줄로 바꾼다. 내 기록이 없으면 빈 값. */
    Optional<MatchSummary> toSummary(MatchResponse match, String accountId) {
        if (match == null || match.data() == null || match.data().attributes() == null) {
            return Optional.empty();
        }
        MatchResponse.MatchAttributes info = match.data().attributes();

        return match.statsOf(accountId).map(me -> new MatchSummary(
                parseTime(info.createdAt()),
                GameLabels.gameMode(info.gameMode()),
                GameLabels.map(info.mapName()),
                GameLabels.matchType(info.matchType()),
                me.winPlace(),
                match.teamCount(),
                me.kills(),
                me.dbnos(),
                (int) me.damageDealt(),
                (int) me.timeSurvived()
        ));
    }

    private Instant parseTime(String createdAt) {
        try {
            return createdAt == null ? null : Instant.parse(createdAt);
        } catch (Exception e) {
            return null;
        }
    }
}
