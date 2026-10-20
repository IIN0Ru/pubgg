package gg.pub.service;

import gg.pub.domain.Farming;
import gg.pub.domain.MatchPlayer;
import gg.pub.domain.MatchRecord;
import gg.pub.domain.PlayCard;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.Title;
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
    void 칭호와_게이지를_기준대로_매긴다() {
        // 경기 2개: 해적 2 + 1 = 3번 → 해적 O
        //          건 교전 6 + 4 = 10, 하이에나 2 + 1 = 3 → 30% → 하이에나 O
        //          보급 1 + 0 = 1, 2판 평균 0.5 → 보급러 O
        //          걸기 10, 걸리기 5 + 5 = 10 → 싸움걸기 50%
        //          자기장 (1.0 + 0.2) ÷ (2 + 2) = 0.3 → 30%
        TelemetryStats a = new TelemetryStats(Map.of(), 0, 6, 5, 2, 2, 1, 1.0, 2);
        TelemetryStats b = new TelemetryStats(Map.of(), 0, 4, 5, 1, 1, 0, 0.2, 2);

        assertThat(PlayCardService.titles(List.of(a, b)))
                .containsExactly(Title.PIRATE, Title.HYENA, Title.SUPPLY_HUNTER);
        assertThat(PlayCardService.zoneEdgePercent(List.of(a, b))).isEqualTo(30);

        when(telemetryService.analyze(eq("m1"), anyString(), eq(ME))).thenReturn(a);
        when(telemetryService.analyze(eq("m2"), anyString(), eq(ME))).thenReturn(b);
        PlayCard card = service.build(List.of(
                match("m1", "squad-fpp", 1, 0, 100, 3),
                match("m2", "squad-fpp", 1, 0, 100, 3)), ME).orElseThrow();

        assertThat(card.fightStartPercent()).isEqualTo(50);
        assertThat(card.zoneEdgePercent()).isEqualTo(30);
        assertThat(card.titles()).hasSize(3);
    }

    @Test
    void 기준에_못_미치면_칭호가_없다() {
        // 해적 2번(3번 미만), 하이에나 1/2(교전 3번 미만이라 판단 안 함), 보급 0
        TelemetryStats t = new TelemetryStats(Map.of(), 0, 2, 0, 1, 2, 0, 0, 0);

        assertThat(PlayCardService.titles(List.of(t))).isEmpty();
        assertThat(PlayCardService.zoneEdgePercent(List.of(t))).isNull();
    }

    @Test
    void 대도시에_절반_이상_내리면_대도시파() {
        // 에란겔 4판: 포친키, 포친키, 학교(3페이즈 전 사망), 농장(대도시 아님)
        List<PlayCardService.Analyzed> analyzed = List.of(
                analyzed("pochinki", new TelemetryStats.Bag(6, 2, 90)),
                analyzed("pochinki", new TelemetryStats.Bag(4, 4, 110)),
                analyzed("school", null),
                analyzed("farm", new TelemetryStats.Bag(8, 0, 100)));

        Farming f = PlayCardService.farming(analyzed);

        assertThat(f.drops()).isEqualTo(4);
        assertThat(f.bigCityDrops()).isEqualTo(3);
        assertThat(f.dropStyle()).isEqualTo(Farming.BIG_CITY);
        assertThat(f.mainCity()).isEqualTo("포친키");
        assertThat(f.phase3Matches()).isEqualTo(3);           // 학교에서 죽은 판은 빼고
        assertThat(f.avgHealsText()).isEqualTo("6.0");        // (6 + 4 + 8) ÷ 3
        assertThat(f.avgThrowablesText()).isEqualTo("2.0");   // (2 + 4 + 0) ÷ 3
        assertThat(f.avgAmmo()).isEqualTo(100);               // (90 + 110 + 100) ÷ 3
    }

    @Test
    void 대도시가_절반보다_적으면_짤파밍파() {
        List<PlayCardService.Analyzed> analyzed = List.of(
                analyzed("pochinki", null),
                analyzed("farm", null),
                analyzed("quarry", null));

        Farming f = PlayCardService.farming(analyzed);

        assertThat(f.dropStyle()).isEqualTo(Farming.SMALL_TOWN);
        assertThat(f.mainCity()).isNull();
        assertThat(f.hasBag()).isFalse();
    }

    private static PlayCardService.Analyzed analyzed(String zone, TelemetryStats.Bag bag) {
        TelemetryStats stats = new TelemetryStats(Map.of(), 0, 0, 0, 0, 0, 0, 0, 0,
                new TelemetryStats.Landing(List.of(zone), 0, 0), bag);
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
