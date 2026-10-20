package gg.pub.service;

import gg.pub.domain.PlayCard;
import gg.pub.domain.TelemetryStats;
import gg.pub.domain.WeaponClass;
import gg.pub.domain.WeaponUsage;
import gg.pub.pubgapi.PubgApiException;
import gg.pub.pubgapi.dto.MatchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        List<MatchResponse> matches = List.of(
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

    /** 테스트용 경쟁전 경기 하나 만들기 (나 한 명 + 텔레메트리 주소) */
    private static MatchResponse match(String id, String gameMode, int kills, int assists,
                                       double damage, int winPlace) {
        MatchResponse.Stats me = new MatchResponse.Stats(
                "N0-RU", ME, kills, 0, assists, 0, damage, 0, 0, 0, 0, 0, 0, 1500, winPlace);

        return new MatchResponse(
                new MatchResponse.Match(id, new MatchResponse.MatchAttributes(
                        "2026-10-19T14:00:00Z", 1800, gameMode, "Baltic_Main", "competitive")),
                List.of(
                        new MatchResponse.Included("participant", "p-" + id,
                                new MatchResponse.IncludedAttributes(me, null)),
                        new MatchResponse.Included("asset", "a-" + id,
                                new MatchResponse.IncludedAttributes(null, "https://telemetry.example/" + id))));
    }
}
