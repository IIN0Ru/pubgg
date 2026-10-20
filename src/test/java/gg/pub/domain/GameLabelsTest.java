package gg.pub.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameLabelsTest {

    @Test
    void 모드_이름을_한글로_바꾼다() {
        assertThat(GameLabels.gameMode("squad-fpp")).isEqualTo("스쿼드 FPP");
        assertThat(GameLabels.gameMode("duo")).isEqualTo("듀오 TPP");
        assertThat(GameLabels.gameMode("normal-solo-fpp")).isEqualTo("솔로 FPP");
        assertThat(GameLabels.gameMode("tdm")).isEqualTo("팀 데스매치");
        assertThat(GameLabels.gameMode("zombie-squad")).isEqualTo("zombie-squad");  // 모르는 모드는 그대로
    }

    @Test
    void 맵_이름을_한글로_바꾼다() {
        assertThat(GameLabels.map("Baltic_Main")).isEqualTo("에란겔");
        assertThat(GameLabels.map("Tiger_Main")).isEqualTo("태이고");
        assertThat(GameLabels.map("New_Main")).isEqualTo("New_Main");
    }
}
