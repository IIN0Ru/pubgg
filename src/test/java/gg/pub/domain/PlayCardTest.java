package gg.pub.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlayCardTest {

    private static PlayCard card(Integer zone) {
        return card(zone, null);
    }

    private static PlayCard card(Integer zone, Farming farming) {
        return new PlayCard(10, 30, 10, 8, 400, "FPP",
                List.of(new WeaponUsage("Beryl M762", WeaponClass.AR_762, 48),
                        new WeaponUsage("Mini 14", WeaponClass.DMR, 31)),
                2.0, 10, zone, List.of(20, 50, 130), null, List.of(), farming);
    }

    @Test
    void 평균_위치_화살표는_오른쪽_위_45도에_찍는다() {
        PlayCard.Dot d = card(50).zoneMarker();   // 반지름 92의 절반 = 46, 46 ÷ √2 ≈ 32.5
        assertThat(d.x()).isCloseTo(142.5, within(0.1));
        assertThat(d.y()).isCloseTo(77.5, within(0.1));
    }

    @Test
    void 판마다_점을_찍고_중심에서_거리는_그_판의_값() {
        List<PlayCard.Dot> dots = card(50).zoneMatchDots();

        assertThat(dots).hasSize(3);
        assertThat(distance(dots.get(0))).isCloseTo(18.4, within(0.2));   // 92 × 20%
        assertThat(distance(dots.get(1))).isCloseTo(46.0, within(0.2));   // 92 × 50%
        assertThat(distance(dots.get(2))).isCloseTo(92.0, within(0.2));   // 경계 밖은 경계에
    }

    @Test
    void 자기장_기록이_없으면_화살표는_중심() {
        assertThat(card(null).zoneMarker()).isEqualTo(new PlayCard.Dot(110, 110));
    }

    private static double distance(PlayCard.Dot d) {
        return Math.hypot(d.x() - 110, d.y() - 110);
    }

    @Test
    void 성향이_뚜렷하면_스타일_태그가_붙는다() {
        Farming farming = new Farming(Farming.BIG_CITY, "포친키", 7, 10, 8, 7.5, 3.2, 214);

        assertThat(card(38, farming).styleTags()).containsExactly("센터 운영", "대도시파밍");
        assertThat(card(70).styleTags()).containsExactly("외곽 운영");
    }

    @Test
    void 자기장_판정_단어() {
        assertThat(card(38).zoneVerdict()).isEqualTo("센터 운영");
        assertThat(card(50).zoneVerdict()).isEqualTo("중간");
        assertThat(card(70).zoneVerdict()).isEqualTo("외곽 운영");
        assertThat(card(null).zoneVerdict()).isEmpty();
    }

    private static PlayCard luck(Integer percent, List<Integer> matches) {
        return new PlayCard(10, 30, 10, 8, 400, "FPP", List.of(), 2.0, 10,
                50, List.of(50), percent, matches, null);
    }

    @Test
    void 자기장_운_판정과_판별_막대() {
        assertThat(luck(72, List.of()).zoneLuckVerdict()).isEqualTo("운 좋음");
        assertThat(luck(60, List.of()).zoneLuckVerdict()).isEqualTo("운 좋음");
        assertThat(luck(55, List.of()).zoneLuckVerdict()).isEqualTo("보통");
        assertThat(luck(40, List.of()).zoneLuckVerdict()).isEqualTo("운 나쁨");
        assertThat(luck(null, List.of()).zoneLuckVerdict()).isEmpty();
        assertThat(luck(null, List.of()).hasZoneLuck()).isFalse();
        // 0%여도 막대가 보이게 4
        assertThat(luck(50, List.of(0, 35, 100)).zoneLuckBars()).containsExactly(4, 35, 100);
    }

    @Test
    void 반반이면_그_항목은_태그가_없다() {
        assertThat(card(50).styleTags()).isEmpty();
    }

    @Test
    void 주무기_이름을_이어_붙인다() {
        assertThat(card(50).weaponNames()).isEqualTo("Beryl M762 · Mini 14");
    }
}
