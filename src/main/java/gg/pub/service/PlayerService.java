package gg.pub.service;

import gg.pub.config.PubgApiProperties;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
import gg.pub.domain.PlayerMatches;
import gg.pub.domain.SeasonRank;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.dto.PlayerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * 플레이어 검색 결과를 만든다. (주방장 역할)
 *
 * 순서:
 *  1. Client 로 닉네임 → 플레이어 정보(계정 ID, 최근 14일 경기 ID 목록)를 받는다
 *  2. 경기들을 동시에 받아서 작은 기록(MatchRecord)으로 줄인다 (이미 받은 경기는 캐시에서)
 *  3. 그중 최신 경쟁전 10경기로 플레이 카드를 계산한다 (PlayCardService)
 *  4. 이번 시즌 경쟁전 티어·기록을 받는다 (SeasonService, 실패하면 티어 없이)
 *
 * 경기 목록, 함께한 팀원, 활동 시간대는 14일 전체 경기를 쓴다.
 */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    /** 하루 종일 하는 사람도 있어서 안전 상한. 14일에 이보다 많으면 최신 경기부터 이만큼만 */
    static final int MAX_MATCHES = 200;

    /** 동시에 받는 경기 수. PUBG 서버에 한꺼번에 너무 많이 요청하지 않도록 */
    static final int PARALLEL_REQUESTS = 8;

    private final PubgApiClient client;
    private final MatchRecordService matchRecordService;
    private final PlayCardService playCardService;
    private final SeasonService seasonService;
    private final int cardMatchLimit;

    public PlayerService(PubgApiClient client, MatchRecordService matchRecordService,
                         PlayCardService playCardService, SeasonService seasonService,
                         PubgApiProperties properties) {
        this.client = client;
        this.matchRecordService = matchRecordService;
        this.playCardService = playCardService;
        this.seasonService = seasonService;
        this.cardMatchLimit = properties.recentMatchLimit() > 0 ? properties.recentMatchLimit() : 10;
    }

    public PlayerMatches getRecentMatches(Platform platform, String nickname) {
        PlayerResponse.Player player = client.findPlayer(platform, nickname);
        String accountId = player.id();

        List<String> matchIds = player.matchIds().stream().limit(MAX_MATCHES).toList();
        List<MatchRecord> matches = loadMatches(platform, matchIds, accountId);

        List<MatchRecord> competitive = matches.stream()
                .filter(MatchRecord::isCompetitive)
                .limit(cardMatchLimit)
                .toList();

        PlayCard card = playCardService.build(competitive, accountId).orElse(null);
        SeasonRank season = seasonService.currentRank(platform, accountId).orElse(null);
        String exactName = player.attributes() != null ? player.attributes().name() : nickname;
        return new PlayerMatches(exactName, platform, card, season, matches);
    }

    /**
     * 경기들을 동시에 받는다 (가상 스레드 + 동시에 최대 8개).
     * 순서는 PUBG 가 준 순서(최신순) 그대로. 실패한 경기와 내 기록이 없는 경기는 빠진다.
     */
    private List<MatchRecord> loadMatches(Platform platform, List<String> matchIds, String accountId) {
        Semaphore permits = new Semaphore(PARALLEL_REQUESTS);
        List<MatchRecord> records = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Optional<MatchRecord>>> futures = new ArrayList<>();
            for (String matchId : matchIds) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return matchRecordService.get(platform, matchId, accountId);
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Future<Optional<MatchRecord>> f : futures) {
                try {
                    f.get().ifPresent(records::add);
                } catch (Exception e) {
                    log.warn("경기 정보를 못 받아서 건너뜀: {}", e.getMessage());
                }
            }
        }
        return records;
    }
}
