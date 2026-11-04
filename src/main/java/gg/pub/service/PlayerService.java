package gg.pub.service;

import gg.pub.config.PubgApiProperties;
import gg.pub.domain.Clan;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.PlayCard;
import gg.pub.domain.PlayerMatches;
import gg.pub.domain.SeasonRank;
import gg.pub.pubgapi.PubgApiClient;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.pubgapi.dto.ClanResponse;
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
 *  5. 클랜에 들어 있으면 클랜 이름·태그를 받는다 (실패하면 클랜 없이)
 *
 * 경기 목록과 함께한 팀원은 그중 최신 30경기만 쓴다 (전부 받으면 너무 오래 걸려서).
 */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    /** 한 번에 불러오는 경기 수. 14일에 이보다 많으면 최신 경기부터 이만큼만 (속도 때문에 200 → 30) */
    static final int MAX_MATCHES = 30;

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

    /**
     * 카드를 뺀 1단계 결과. 화면은 이걸로 먼저 그리고, 카드(텔레메트리 분석, 제일 오래 걸림)는 따로 만든다.
     *
     * @param result      카드가 비어 있는 검색 결과 (경기 목록·시즌·클랜 포함)
     * @param competitive 카드를 만들 최신 경쟁전 경기
     * @param accountId   PUBG 계정 ID
     */
    public record Basics(PlayerMatches result, List<MatchRecord> competitive, String accountId) {
        /** 카드를 만들 경쟁전 경기가 하나라도 있으면 true */
        public boolean needsCard() {
            return !competitive.isEmpty();
        }
    }

    /** 카드까지 다 만든 검색 결과 (기다려서 한 번에 받고 싶을 때) */
    public PlayerMatches getRecentMatches(Platform platform, String nickname) {
        Basics basics = getBasics(platform, nickname);
        return basics.result().withCard(buildCard(basics));
    }

    /**
     * 1단계: 선수 조회 → 경기 받기. 시즌·클랜은 경기 받기와 동시에 불러온다 (서로 기다릴 필요가 없어서).
     */
    public Basics getBasics(Platform platform, String nickname) {
        long start = System.nanoTime();
        PlayerResponse.Player player = client.findPlayer(platform, nickname);
        String accountId = player.id();
        long afterPlayer = System.nanoTime();

        String clanId = player.attributes() != null ? player.attributes().clanId() : null;
        List<String> matchIds = player.matchIds().stream().limit(MAX_MATCHES).toList();

        List<MatchRecord> matches;
        SeasonRank season;
        Clan clan;
        try (ExecutorService side = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<SeasonRank> seasonTask = side.submit(
                    () -> seasonService.currentRank(platform, accountId).orElse(null));
            Future<Clan> clanTask = side.submit(() -> loadClan(platform, clanId));
            matches = loadMatches(platform, matchIds, accountId);
            season = await(seasonTask, "시즌");
            clan = await(clanTask, "클랜");
        }
        long end = System.nanoTime();

        String exactName = player.attributes() != null ? player.attributes().name() : nickname;
        List<MatchRecord> competitive = matches.stream()
                .filter(MatchRecord::isCompetitive)
                .limit(cardMatchLimit)
                .toList();

        log.info("검색 1단계 [{}] 합계 {}ms = 선수조회 {}ms + (경기 {}/{}개 받기 · 시즌 · 클랜 동시에) {}ms",
                platform.getShard(), ms(start, end), ms(start, afterPlayer),
                matches.size(), matchIds.size(), ms(afterPlayer, end));
        return new Basics(new PlayerMatches(exactName, platform, null, season, matches, clan), competitive, accountId);
    }

    /** 2단계: 카드 만들기 (텔레메트리 분석). 경쟁전 기록이 없으면 null */
    public PlayCard buildCard(Basics basics) {
        long start = System.nanoTime();
        Runtime rt = Runtime.getRuntime();
        PlayCard card = playCardService.build(basics.competitive(), basics.accountId()).orElse(null);
        // 서버가 얼마나 작은지 확인하려고 같이 남긴다 (CPU 개수, 쓸 수 있는 최대 메모리)
        log.info("카드 만들기 [{}] 경쟁전 {}판 {}ms (서버 CPU {}개, 최대 메모리 {}MB)",
                basics.result().platform().getShard(), basics.competitive().size(), ms(start, System.nanoTime()),
                rt.availableProcessors(), rt.maxMemory() / (1024 * 1024));
        return card;
    }

    private static <T> T await(Future<T> task, String what) {
        try {
            return task.get();
        } catch (Exception e) {
            log.warn("{} 정보를 못 받음: {}", what, e.getMessage());
            return null;
        }
    }

    private static long ms(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000;
    }

    /** 클랜 이름·태그. 클랜이 없거나 못 받으면 null (클랜 때문에 검색이 실패하지 않게) */
    private Clan loadClan(Platform platform, String clanId) {
        if (clanId == null || clanId.isBlank()) {
            return null;
        }
        try {
            ClanResponse res = client.getClan(platform, clanId);
            if (res == null || res.data() == null || res.data().attributes() == null) {
                return null;
            }
            return new Clan(res.data().attributes().clanName(), res.data().attributes().clanTag());
        } catch (PubgApiException e) {
            log.warn("클랜 정보를 못 받음: {}", e.getMessage());
            return null;
        }
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
