package gg.pub.service;

import gg.pub.config.PubgApiProperties;
import gg.pub.domain.GameLabels;
import gg.pub.domain.MatchSummary;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayerMatches;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.dto.MatchResponse;
import gg.pub.pubgapi.dto.PlayerResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 플레이어 관련 실제 일을 처리한다. (주방장 역할)
 *
 * 순서:
 *  1. Client 로 닉네임 → 플레이어 정보(계정 ID, 최근 경기 ID 목록)를 받는다
 *  2. 최근 경기 ID 마다 경기 기록을 받는다
 *  3. 경기 기록 중 "내 기록"만 골라 화면용 MatchSummary 로 바꾼다
 *
 * 나중에 칭호·게이지 계산도 이런 Service 클래스에 들어간다.
 */
@Service
public class PlayerService {

    private final PubgApiClient client;
    private final int recentMatchLimit;

    public PlayerService(PubgApiClient client, PubgApiProperties properties) {
        this.client = client;
        this.recentMatchLimit = properties.recentMatchLimit() > 0 ? properties.recentMatchLimit() : 10;
    }

    public PlayerMatches getRecentMatches(Platform platform, String nickname) {
        PlayerResponse.Player player = client.findPlayer(platform, nickname);
        String accountId = player.id();

        List<MatchSummary> matches = player.matchIds().stream()
                .limit(recentMatchLimit)
                .map(matchId -> client.getMatch(platform, matchId))
                .map(match -> toSummary(match, accountId))
                .flatMap(Optional::stream)   // 내 기록을 못 찾은 경기는 건너뛴다
                .toList();

        String exactName = player.attributes() != null ? player.attributes().name() : nickname;
        return new PlayerMatches(exactName, platform, matches);
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
