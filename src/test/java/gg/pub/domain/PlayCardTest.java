package gg.pub.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlayCardTest {

    private static PlayCard card(Integer fight, Integer zone) {
        return new PlayCard(10, 30, 10, 8, 400, "FPP", List.of(), 2.0, 10, List.of(), fight, zone, null);
    }

    @Test
    void 싸움걸리기는_100에서_싸움걸기를_뺀_값() {
        assertThat(card(65, null).fightReceivePercent()).isEqualTo(35);
    }

    @Test
    void 자기장_지도_점_위치를_계산한다() {
        PlayCard c = card(null, 50);   // 중심과 경계의 딱 중간
        assertThat(c.zoneRingRadius()).isEqualTo(28.0);                    // 반지름 56의 절반
        assertThat(c.zoneMarkerOffset()).isCloseTo(19.80, within(0.01));   // 28 ÷ √2
    }

    @Test
    void 자기장_기록이_없으면_지도_값은_0() {
        assertThat(card(null, null).zoneRingRadius()).isZero();
    }

    private static PlayCard card(Integer fight, Integer zone, Farming farming) {
        return new PlayCard(10, 30, 10, 8, 400, "FPP",
                List.of(new WeaponUsage("Beryl M762", WeaponClass.AR_762, 48),
                        new WeaponUsage("Mini 14", WeaponClass.DMR, 31)),
                2.0, 10, List.of(), fight, zone, farming);
    }

    @Test
    void 성향이_뚜렷하면_스타일_태그가_붙는다() {
        Farming farming = new Farming(Farming.BIG_CITY, "포친키", 7, 10, 8, 7.5, 3.2, 214);

        assertThat(card(65, 38, farming).styleTags()).containsExactly("먼저 거는 편", "센터 운영", "대도시파");
        assertThat(card(30, 70, null).styleTags()).containsExactly("받아치는 편", "외곽 운영");
    }

    @Test
    void 반반이면_그_항목은_태그가_없다() {
        assertThat(card(50, 50, null).styleTags()).isEmpty();
    }

    @Test
    void 주무기_이름을_이어_붙인다() {
        assertThat(card(50, 50, null).weaponNames()).isEqualTo("Beryl M762 · Mini 14");
    }
}

