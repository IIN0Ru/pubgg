package gg.pub.service;

import gg.pub.domain.PlayCard;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.Title;
import gg.pub.domain.WeaponCatalog;
import gg.pub.domain.WeaponUsage;
import gg.pub.pubgapi.dto.MatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 경쟁전 경기들로 플레이 카드 기본 정보를 계산한다.
 *
 *  - 경기 요약(MatchResponse)으로 계산: KDA, 평균 딜량, FPP/TPP
 *  - 텔레메트리로 계산: 주무기 2개, 평균 투척 개수, 칭호, 게이지
 *
 * 칭호 기준 (실제 데이터를 보고 조정 예정):
 *  - 해적: 10경기에서 해적 성공 3번 이상
 *  - 하이에나: 우리 팀이 건 교전 중 30% 이상이 하이에나 (건 교전이 3번 이상일 때만)
 *  - 보급러: 한 판 평균 보급상자 0.5개 이상
 *
 * 텔레메트리는 경기마다 따로 받아야 해서, 10경기를 동시에 받는다(가상 스레드).
 * 한 경기 텔레메트리가 실패해도 나머지 경기로 계산한다.
 */
@Service
public class PlayCardService {

    private static final Logger log = LoggerFactory.getLogger(PlayCardService.class);

    static final int PIRATE_MIN_COUNT = 3;
    static final double HYENA_MIN_RATIO = 0.3;
    static final int HYENA_MIN_ENGAGEMENTS = 3;
    static final double SUPPLY_MIN_PER_MATCH = 0.5;

    private final TelemetryService telemetryService;

    public PlayCardService(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * @param matches   경쟁전 경기들 (내 기록이 있는 경기만)
     * @param accountId 내 계정 ID
     * @return 경기가 없으면 빈 값
     */
    public Optional<PlayCard> build(List<MatchResponse> matches, String accountId) {
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        // 1) 경기 요약으로 계산
        int kills = 0, assists = 0, deaths = 0, fpp = 0;
        double damage = 0;
        for (MatchResponse m : matches) {
            MatchResponse.Stats me = m.statsOf(accountId).orElseThrow();
            kills += me.kills();
            assists += me.assists();
            damage += me.damageDealt();
            if (me.winPlace() != 1) {
                deaths++;
            }
            if (m.isFpp()) {
                fpp++;
            }
        }
        String perspective = fpp * 2 >= matches.size() ? "FPP" : "TPP";

        // 2) 텔레메트리로 계산
        List<TelemetryStats> telemetry = loadTelemetry(matches, accountId);
        int throwables = telemetry.stream().mapToInt(TelemetryStats::throwables).sum();
        double avgThrowables = telemetry.isEmpty() ? 0 : (double) throwables / telemetry.size();

        int started = telemetry.stream().mapToInt(TelemetryStats::engagementsStarted).sum();
        int received = telemetry.stream().mapToInt(TelemetryStats::engagementsReceived).sum();

        return Optional.of(new PlayCard(
                matches.size(),
                kills,
                assists,
                deaths,
                (int) Math.round(damage / matches.size()),
                perspective,
                topWeapons(telemetry, 2),
                avgThrowables,
                telemetry.size(),
                titles(telemetry),
                started + received == 0 ? null : (int) Math.round(started * 100.0 / (started + received)),
                zoneEdgePercent(telemetry)
        ));
    }

    /** 경기마다 텔레메트리를 동시에 받아 분석한다. 실패한 경기는 건너뛴다. */
    private List<TelemetryStats> loadTelemetry(List<MatchResponse> matches, String accountId) {
        List<TelemetryStats> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<TelemetryStats>> futures = new ArrayList<>();
            for (MatchResponse m : matches) {
                Optional<String> url = m.telemetryUrl();
                if (url.isEmpty()) {
                    continue;
                }
                String matchId = m.data().id();
                futures.add(executor.submit(() -> telemetryService.analyze(matchId, url.get(), accountId)));
            }
            for (Future<TelemetryStats> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    log.warn("텔레메트리 분석 실패, 이 경기는 건너뜀: {}", e.getMessage());
                }
            }
        }
        return results;
    }

    /** 경기들의 주무기 보유 시간을 총 이름별로 합쳐서, 오래 쓴 순서로 limit 개 */
    static List<WeaponUsage> topWeapons(List<TelemetryStats> telemetry, int limit) {
        // itemId 가 달라도 같은 총(예: M416 두 종류)은 이름으로 합친다
        Map<String, Long> secondsByName = new HashMap<>();
        Map<String, WeaponCatalog.Weapon> weaponByName = new LinkedHashMap<>();

        for (TelemetryStats t : telemetry) {
            t.weaponSeconds().forEach((itemId, sec) ->
                    WeaponCatalog.find(itemId).ifPresent(w -> {
                        secondsByName.merge(w.name(), sec, Long::sum);
                        weaponByName.putIfAbsent(w.name(), w);
                    }));
        }

        long total = secondsByName.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return List.of();
        }

        return secondsByName.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new WeaponUsage(
                        e.getKey(),
                        weaponByName.get(e.getKey()).type(),
                        (int) Math.round(e.getValue() * 100.0 / total)))
                .toList();
    }

    /** 칭호 기준을 넘은 것만 */
    static List<Title> titles(List<TelemetryStats> telemetry) {
        if (telemetry.isEmpty()) {
            return List.of();
        }
        List<Title> titles = new ArrayList<>();

        int pirate = telemetry.stream().mapToInt(TelemetryStats::pirateKnocks).sum();
        if (pirate >= PIRATE_MIN_COUNT) {
            titles.add(Title.PIRATE);
        }

        int started = telemetry.stream().mapToInt(TelemetryStats::engagementsStarted).sum();
        int hyena = telemetry.stream().mapToInt(TelemetryStats::hyenaEngagements).sum();
        if (started >= HYENA_MIN_ENGAGEMENTS && (double) hyena / started >= HYENA_MIN_RATIO) {
            titles.add(Title.HYENA);
        }

        int carePackages = telemetry.stream().mapToInt(TelemetryStats::carePackages).sum();
        if ((double) carePackages / telemetry.size() >= SUPPLY_MIN_PER_MATCH) {
            titles.add(Title.SUPPLY_HUNTER);
        }
        return List.copyOf(titles);
    }

    /** 3페이즈부터 자기장 안 평균 위치. 0 = 중심, 100 = 경계 (경계 밖은 100으로) */
    static Integer zoneEdgePercent(List<TelemetryStats> telemetry) {
        double sum = telemetry.stream().mapToDouble(TelemetryStats::zoneRatioSum).sum();
        int samples = telemetry.stream().mapToInt(TelemetryStats::zoneSamples).sum();
        if (samples == 0) {
            return null;
        }
        return (int) Math.round(Math.min(1.0, sum / samples) * 100);
    }
}
