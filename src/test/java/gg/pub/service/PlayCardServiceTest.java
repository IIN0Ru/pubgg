package gg.pub.service;

import gg.pub.domain.Farming;
import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.PlayCard;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.WeaponClass;
import gg.pub.domain.WeaponUsage;
import gg.pub.pubgapi.PubgApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 플레이 카드 계산 확인.
 *
 * 예시 경쟁전 3경기:
 *  | 경기 | 킬 | 어시 | 딜 | 등수 | 시점 | 텔레메트리                          | 투척 |
 *  | m1   | 4  | 1    | 400 | 1   | FPP | M416 300초, Mini14 100초          | 3    |
 *  | m2   | 2  | 2    | 300 | 5   | FPP | M416(다른 ID) 100초, Beryl 200초  | 1    |
 *  | m3   | 1  | 0    | 200 | 10  | TPP | 실패                               | -    |
 *
 *  → KDA = (7 + 3) ÷ 2(죽은 판) = 5.00, 평균 딜 300, FPP
 *  → 주무기: M416 400초(57%), Beryl 200초(29%)   ※ 전체 700초 기준
 *  → 평균 투척 = (3 + 1) ÷ 2(성공한 경기) = 2.0
 */
class PlayCardServiceTest {

    private static final String ME = "account.me";

    private TelemetryService telemetryService;
    private PlayCardService service;

    @BeforeEach
    void setUp() {
        telemetryService = mock(TelemetryService.class);
        service = new PlayCardService(telemetryService);
    }

    @Test
    void 경쟁전_경기로_플레이_카드를_계산한다() {
        List<MatchRecord> matches = List.of(
                match("m1", "squad-fpp", 4, 1, 400, 1),
                match("m2", "squad-fpp", 2, 2, 300, 5),
                match("m3", "squad", 1, 0, 200, 10));

        when(telemetryService.analyze(eq("m1"), anyString(), eq(ME))).thenReturn(new TelemetryStats(
                Map.of("Item_Weapon_HK416_C", 300L, "Item_Weapon_Mini14_C", 100L), 3));
        when(telemetryService.analyze(eq("m2"), anyString(), eq(ME))).thenReturn(new TelemetryStats(
                Map.of("Item_Weapon_Duncans_M416_C", 100L, "Item_Weapon_BerylM762_C", 200L), 1));
        when(telemetryService.analyze(eq("m3"), anyString(), eq(ME)))
                .thenThrow(new PubgApiException("텔레메트리 실패"));

        PlayCard card = service.build(matches, ME).orElseThrow();

        assertThat(card.matches()).isEqualTo(3);
        assertThat(card.kdaText()).isEqualTo("5.00");
        assertThat(card.avgDamage()).isEqualTo(300);
        assertThat(card.perspective()).isEqualTo("FPP");
        assertThat(card.analyzedMatches()).isEqualTo(2);
        assertThat(card.avgThrowablesText()).isEqualTo("2.0");
        assertThat(card.mainWeapons()).containsExactly(
                new WeaponUsage("M416", WeaponClass.AR_556, 57),
                new WeaponUsage("Beryl M762", WeaponClass.AR_762, 29));
    }

    @Test
    void 경쟁전_경기가_없으면_카드도_없다() {
        assertThat(service.build(List.of(), ME)).isEmpty();
    }

    @Test
    void 한_번도_안_죽었으면_KDA는_킬_더하기_어시() {
        when(telemetryService.analyze(anyString(), anyString(), anyString()))
                .thenReturn(new TelemetryStats(Map.of(), 0));

        PlayCard card = service.build(List.of(match("m1", "squad-fpp", 6, 2, 500, 1)), ME).orElseThrow();

        assertThat(card.kdaText()).isEqualTo("8.00");
        assertThat(card.hasWeapons()).isFalse();
    }

    @Test
    void 자기장_운영을_경기별과_전체로_매긴다() {
        // 자기장 (1.0 + 0.2) ÷ (2 + 2) = 0.3 → 30%, 경기별 1.0÷2 = 50%, 0.2÷2 = 10%
        TelemetryStats a = new TelemetryStats(Map.of(), 0, 1.0, 2);
        TelemetryStats b = new TelemetryStats(Map.of(), 0, 0.2, 2);

        assertThat(PlayCardService.zoneEdgePercent(List.of(a, b))).isEqualTo(30);
        assertThat(PlayCardService.zoneMatchPercents(List.of(a, b))).containsExactly(50, 10);

        when(telemetryService.analyze(eq("m1"), anyString(), eq(ME))).thenReturn(a);
        when(telemetryService.analyze(eq("m2"), anyString(), eq(ME))).thenReturn(b);
        PlayCard card = service.build(List.of(
                match("m1", "squad-fpp", 1, 0, 100, 3),
                match("m2", "squad-fpp", 1, 0, 100, 3)), ME).orElseThrow();

        assertThat(card.zoneEdgePercent()).isEqualTo(30);
    }

    @Test
    void 자기장_운은_새_원이_뜬_직후_위치로() {
        // 경기 1: 2페이즈 동안 1.0 + 0.6 → 80%, 경기 2: 1페이즈 0.2 → 20%, 경기 3: 기록 없음
        // 전체: (1.0 + 0.6 + 0.2) ÷ 3 = 0.6 → 60%
        TelemetryStats a = luck(1.6, 2);
        TelemetryStats b = luck(0.2, 1);
        TelemetryStats none = luck(0, 0);

        assertThat(PlayCardService.zoneLuckPercent(List.of(a, b, none))).isEqualTo(60);
        assertThat(PlayCardService.zoneLuckMatchPercents(List.of(a, b, none))).containsExactly(80, 20);
        assertThat(PlayCardService.zoneLuckPercent(List.of(none))).isNull();
    }

    private static TelemetryStats luck(double sum, int samples) {
        return new TelemetryStats(Map.of(), 0, 0, 0, sum, samples, null, null, null);
    }

    @Test
    void 자기장_기록이_없으면_비운다() {
        TelemetryStats t = new TelemetryStats(Map.of(), 0);

        assertThat(PlayCardService.zoneEdgePercent(List.of(t))).isNull();
    }

    @Test
    void 헬멧_가방_조끼를_대도시에서_다_모은_판이_절반_이상이면_대도시파밍() {
        // 에란겔 4판: 다 모은 곳이 포친키, 포친키, 학교(3페이즈 전 사망), 농장(대도시 아님)
        List<PlayCardService.Analyzed> analyzed = List.of(
                analyzed("pochinki", new TelemetryStats.Bag(6, 2, 90)),
                analyzed("pochinki", new TelemetryStats.Bag(4, 4, 110)),
                analyzed("school", null),
                analyzed("farm", new TelemetryStats.Bag(8, 0, 100)));

        Farming f = PlayCardService.farming(analyzed);

        assertThat(f.farmedMatches()).isEqualTo(4);
        assertThat(f.bigCityFarms()).isEqualTo(3);
        assertThat(f.farmStyle()).isEqualTo(Farming.BIG_CITY);
        assertThat(f.mainCity()).isEqualTo("포친키");
        assertThat(f.phase3Matches()).isEqualTo(3);           // 학교에서 죽은 판은 빼고
        assertThat(f.avgHealsText()).isEqualTo("6.0");        // (6 + 4 + 8) ÷ 3
        assertThat(f.avgThrowablesText()).isEqualTo("2.0");   // (2 + 4 + 0) ÷ 3
        assertThat(f.avgAmmo()).isEqualTo(100);               // (90 + 110 + 100) ÷ 3
    }

    @Test
    void 대도시가_절반보다_적으면_짤파밍() {
        List<PlayCardService.Analyzed> analyzed = List.of(
                analyzed("pochinki", null),
                analyzed("farm", null),
                analyzed("quarry", null));

        Farming f = PlayCardService.farming(analyzed);

        assertThat(f.farmStyle()).isEqualTo(Farming.SMALL_TOWN);
        assertThat(f.mainCity()).isNull();
        assertThat(f.hasBag()).isFalse();
    }

    @Test
    void 다_모으기_전에_죽은_판은_빼고_센다() {
        // 다 모은 판: 농장, 채석장 (짤파밍) / 포친키에 내렸지만 다 모으기 전에 죽은 판 2개는 빠짐
        List<PlayCardService.Analyzed> analyzed = List.of(
                analyzed("farm", null),
                analyzed("quarry", null),
                analyzed((String) null, null),
                analyzed((String) null, null));

        Farming f = PlayCardService.farming(analyzed);

        assertThat(f.farmedMatches()).isEqualTo(2);
        assertThat(f.farmStyle()).isEqualTo(Farming.SMALL_TOWN);
        assertThat(Farming.SMALL_TOWN).isEqualTo("짤파밍");
    }

    @Test
    void 아무_판도_다_모으지_못했으면_파밍_장소_없음() {
        Farming f = PlayCardService.farming(List.of(analyzed((String) null, null)));
        assertThat(f.hasFarmStyle()).isFalse();
    }

    /** zone: 헬멧·가방·조끼를 다 모은 곳 (null 이면 다 모으기 전에 죽음). 착지는 늘 포친키 (판단에 안 씀) */
    private static PlayCardService.Analyzed analyzed(String zone, TelemetryStats.Bag bag) {
        TelemetryStats stats = new TelemetryStats(Map.of(), 0, 0, 0, 0, 0,
                new TelemetryStats.Landing(List.of("pochinki"), 0, 0), bag,
                zone == null ? null : new TelemetryStats.Landing(List.of(zone), 0, 0));
        return new PlayCardService.Analyzed(match("m", "squad-fpp", 0, 0, 0, 5), stats);
    }

    /** 테스트용 경쟁전 경기 하나 만들기 (나 한 명 + 텔레메트리 주소) */
    private static MatchRecord match(String id, String gameMode, int kills, int assists,
                                     double damage, int winPlace) {
        MatchPlayer me = new MatchPlayer("N0-RU", ME, kills, 0, assists, (int) damage, winPlace,
                1500, 0, 0, 0, winPlace == 1 ? "alive" : "byplayer");
        return new MatchRecord(id, Instant.parse("2026-10-19T14:00:00Z"), gameMode, "Baltic_Main",
                "competitive", 16, "https://telemetry.example/" + id, me, List.of());
    }
}
