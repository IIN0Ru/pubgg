package gg.pub.service;

import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.dto.MatchResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 경기 하나를 받아서 작은 기록(MatchRecord)으로 줄이고, 저장(캐시)해 둔다.
 *
 * 끝난 경기는 바뀌지 않으니 (경기, 플레이어) 한 쌍은 한 번만 받으면 된다.
 * 그래서 "전적 갱신"을 눌러도 새로 생긴 경기만 실제로 PUBG API 를 부른다.
 *
 * 주의: TelemetryService 처럼 서버 메모리 캐시라 서버를 다시 켜면 비워진다.
 */
@Service
public class MatchRecordService {

    private final PubgApiClient client;

    public MatchRecordService(PubgApiClient client) {
        this.client = client;
    }

    /** 내 기록이 없는 경기면 빈 값 (Spring 캐시는 Optional 을 알아서 풀어서 저장한다) */
    @Cacheable(cacheNames = "matches", key = "#matchId + ':' + #accountId")
    public Optional<MatchRecord> get(Platform platform, String matchId, String accountId) {
        return toRecord(client.getMatch(platform, matchId), accountId);
    }

    /** 경기 응답 → 나와 팀원 기록만 남긴 MatchRecord */
    static Optional<MatchRecord> toRecord(MatchResponse match, String accountId) {
        if (match == null || match.data() == null || match.data().attributes() == null) {
            return Optional.empty();
        }
        MatchResponse.MatchAttributes info = match.data().attributes();

        return match.statsOf(accountId).map(me -> new MatchRecord(
                match.data().id(),
                parseTime(info.createdAt()),
                info.gameMode(),
                info.mapName(),
                info.matchType(),
                match.teamCount(),
                match.telemetryUrl().orElse(null),
                toPlayer(me),
                match.teammatesOf(accountId).stream().map(MatchRecordService::toPlayer).toList()
        ));
    }

    private static MatchPlayer toPlayer(MatchResponse.Stats s) {
        return new MatchPlayer(
                s.name(),
                s.playerId(),
                s.kills(),
                s.dbnos(),
                s.assists(),
                (int) s.damageDealt(),
                s.winPlace(),
                (int) s.timeSurvived(),
                s.headshotKills(),
                (int) s.longestKill(),
                s.revives(),
                s.deathType());
    }

    private static Instant parseTime(String createdAt) {
        try {
            return createdAt == null ? null : Instant.parse(createdAt);
        } catch (Exception e) {
            return null;
        }
    }
}
