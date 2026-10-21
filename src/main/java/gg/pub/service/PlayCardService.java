package gg.pub.service;

import gg.pub.domain.DropZones;
import gg.pub.domain.Farming;
import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.PlayCard;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.Title;
import gg.pub.domain.WeaponCatalog;
import gg.pub.domain.WeaponUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 경쟁전 경기들로 플레이 카드 기본 정보를 계산한다.
 *
 *  - 경기 기록(MatchRecord)으로 계산: KDA, 평균 딜량, FPP/TPP
 *  - 텔레메트리로 계산: 주무기 2개, 평균 투척 개수, 칭호, 게이지, 파밍(착지·3페이즈 가방)
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
    public Optional<PlayCard> build(List<MatchRecord> matches, String accountId) {
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        // 1) 경기 요약으로 계산
        int kills = 0, assists = 0, deaths = 0, fpp = 0;
        double damage = 0;
        for (MatchRecord m : matches) {
            MatchPlayer me = m.me();
            kills += me.kills();
            assists += me.assists();
            damage += me.damage();
            if (me.rank() != 1) {
                deaths++;
            }
            if (m.isFpp()) {
                fpp++;
            }
        }
        String perspective = fpp * 2 >= matches.size() ? "FPP" : "TPP";

        // 2) 텔레메트리로 계산
        List<Analyzed> analyzed = loadTelemetry(matches, accountId);
        List<TelemetryStats> telemetry = analyzed.stream().map(Analyzed::stats).toList();
        int throwables = telemetry.stream().mapToInt(TelemetryStats::throwables).sum();
        double avgThrowables = telemetry.isEmpty() ? 0 : (double) throwables / telemetry.size();

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
                zoneEdgePercent(telemetry),
                zoneMatchPercents(telemetry),
                zoneLuckPercent(telemetry),
                zoneLuckMatchPercents(telemetry),
                farming(analyzed)
        ));
    }

    /** 경기와 그 경기의 텔레메트리 분석 결과 (맵 이름이 필요해서 같이 둔다) */
    record Analyzed(MatchRecord match, TelemetryStats stats) {
    }

    /** 경기마다 텔레메트리를 동시에 받아 분석한다. 실패한 경기는 건너뛴다. */
    private List<Analyzed> loadTelemetry(List<MatchRecord> matches, String accountId) {
        List<Analyzed> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<MatchRecord> submitted = new ArrayList<>();
            List<Future<TelemetryStats>> futures = new ArrayList<>();
            for (MatchRecord m : matches) {
                String url = m.telemetryUrl();
                if (url == null) {
                    continue;
                }
                submitted.add(m);
                futures.add(executor.submit(() -> telemetryService.analyze(m.matchId(), url, accountId)));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(new Analyzed(submitted.get(i), futures.get(i).get()));
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

    /** 운영: 3페이즈부터 자기장이 줄어들기 시작할 때 평균 위치. 0 = 중심, 100 = 경계 (경계 밖은 100으로) */
    static Integer zoneEdgePercent(List<TelemetryStats> telemetry) {
        double sum = telemetry.stream().mapToDouble(TelemetryStats::zoneRatioSum).sum();
        int samples = telemetry.stream().mapToInt(TelemetryStats::zoneSamples).sum();
        if (samples == 0) {
            return null;
        }
        return (int) Math.round(Math.min(1.0, sum / samples) * 100);
    }

    /** 운영을 경기마다 (%). 3페이즈가 줄어들기 시작할 때까지 못 간 경기는 빠진다 */
    static List<Integer> zoneMatchPercents(List<TelemetryStats> telemetry) {
        return telemetry.stream()
                .filter(t -> t.zoneSamples() > 0)
                .map(t -> (int) Math.round(Math.min(1.0, t.zoneRatioSum() / t.zoneSamples()) * 100))
                .toList();
    }

    /** 자기장 운: 3페이즈부터 새 원이 뜬 직후 평균. 100 = 늘 중심, 0 = 늘 경계나 밖 */
    static Integer zoneLuckPercent(List<TelemetryStats> telemetry) {
        double sum = telemetry.stream().mapToDouble(TelemetryStats::zoneLuckSum).sum();
        int samples = telemetry.stream().mapToInt(TelemetryStats::zoneLuckSamples).sum();
        if (samples == 0) {
            return null;
        }
        return (int) Math.round(sum / samples * 100);
    }

    /** 자기장 운을 경기마다 (%) */
    static List<Integer> zoneLuckMatchPercents(List<TelemetryStats> telemetry) {
        return telemetry.stream()
                .filter(t -> t.zoneLuckSamples() > 0)
                .map(t -> (int) Math.round(t.zoneLuckSum() / t.zoneLuckSamples() * 100))
                .toList();
    }

    /**
     * 파밍 정보.
     *  - 착지: 대도시에 내린 판이 절반 이상이면 대도시파, 아니면 짤파밍파. 대도시파면 가장 많이 내린 도시도
     *  - 가방: 3페이즈까지 살아남은 판들의 3페이즈 시작 순간 평균
     */
    static Farming farming(List<Analyzed> analyzed) {
        int drops = 0;
        int bigDrops = 0;
        Map<String, Integer> cityCount = new LinkedHashMap<>();

        for (Analyzed a : analyzed) {
            TelemetryStats.Landing landing = a.stats().landing();
            if (landing == null) {
                continue;
            }
            drops++;
            String mapName = a.match().mapName();
            Optional<String> city = DropZones.bigCity(mapName, landing.zones(), landing.x(), landing.y());
            if (city.isPresent()) {
                bigDrops++;
                cityCount.merge(city.get(), 1, Integer::sum);
            }
        }

        String dropStyle = null;
        String mainCity = null;
        if (drops > 0) {
            boolean bigCity = bigDrops * 2 >= drops;
            dropStyle = bigCity ? Farming.BIG_CITY : Farming.SMALL_TOWN;
            if (bigCity) {
                mainCity = cityCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
            }
        }

        List<TelemetryStats.Bag> bags = analyzed.stream()
                .map(a -> a.stats().phase3())
                .filter(Objects::nonNull)
                .toList();
        double avgHeals = bags.stream().mapToInt(TelemetryStats.Bag::heals).average().orElse(0);
        double avgThrowables = bags.stream().mapToInt(TelemetryStats.Bag::throwables).average().orElse(0);
        int avgAmmo = (int) Math.round(bags.stream().mapToInt(TelemetryStats.Bag::ammoPicked).average().orElse(0));

        return new Farming(dropStyle, mainCity, bigDrops, drops, bags.size(), avgHeals, avgThrowables, avgAmmo);
    }
}
