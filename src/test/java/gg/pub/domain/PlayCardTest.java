package gg.pub.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlayCardTest {

    private static PlayCard card(Integer fight, Integer zone) {
        return new PlayCard(10, 30, 10, 8, 400, "FPP", List.of(), 2.0, 10, List.of(), fight, zone);
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
}
