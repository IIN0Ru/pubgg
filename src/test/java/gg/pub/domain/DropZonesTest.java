package gg.pub.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DropZonesTest {

    private static final double CM_PER_PX = 816_000.0 / 819;

    @Test
    void 에란겔은_지역_ID로_판단한다() {
        assertThat(DropZones.bigCity("Baltic_Main", List.of("pochinki"), 0, 0)).contains("포친키");
        assertThat(DropZones.bigCity("Erangel_Main", List.of("school"), 0, 0)).contains("학교·아파트");
        assertThat(DropZones.bigCity("Baltic_Main", List.of("farm"), 0, 0)).isEmpty();
    }

    @Test
    void 미라마와_태이고도_지역_ID로_판단한다() {
        assertThat(DropZones.bigCity("Desert_Main", List.of("LosLeones"), 0, 0)).contains("로스 레오네스");  // 대소문자 무시
        assertThat(DropZones.bigCity("Tiger_Main", List.of("hosan"), 0, 0)).contains("호산");
    }

    @Test
    void 추가한_대도시도_찾는다() {
        assertThat(DropZones.bigCity("Baltic_Main", List.of("severny"), 0, 0)).contains("세베르니");
        assertThat(DropZones.bigCity("Desert_Main", List.of("lmpala"), 0, 0)).contains("임팔라");   // 공식 목록 표기
        assertThat(DropZones.bigCity("Tiger_Main", List.of("hosanprison"), 0, 0)).contains("호산 교도소");
        assertThat(DropZones.bigCity("Neon_Main", List.of(), 592 * CM_PER_PX, 158 * CM_PER_PX)).contains("무호판");
        // 사용자 목록에서 빠진 곳은 대도시가 아님
        assertThat(DropZones.bigCity("Desert_Main", List.of("heciendadelpatron"), 0, 0)).isEmpty();
    }

    @Test
    void 론도는_좌표로_판단한다() {
        // 자데나 시티 중심 (이미지 720, 585 px)
        assertThat(DropZones.bigCity("Neon_Main", List.of(), 720 * CM_PER_PX, 585 * CM_PER_PX)).contains("자데나 시티");
        // 맵 한가운데 빈 곳
        assertThat(DropZones.bigCity("Neon_Main", List.of(), 400 * CM_PER_PX, 480 * CM_PER_PX)).isEmpty();
    }

    @Test
    void 랭크_맵이_아니면_판단하지_않는다() {
        assertThat(DropZones.bigCity("Savage_Main", List.of("bootcamp"), 0, 0)).isEmpty();
        assertThat(DropZones.bigCity(null, List.of("pochinki"), 0, 0)).isEmpty();
    }
}
