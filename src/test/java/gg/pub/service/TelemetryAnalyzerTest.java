package gg.pub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.TelemetryStats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예시 텔레메트리(src/test/resources/pubg/telemetry.json)로 계산을 확인한다.
 *
 * 예시 상황 (14:13:00 시작, 14:16:20 사망):
 *  - M416   14:13:00 장착 → 사망까지  = 200초
 *  - Kar98k 14:13:10 장착 → 14:14:10 해제 = 60초
 *  - Mini14 14:14:20 장착 → 사망까지  = 120초
 *  - 프라이팬은 주무기가 아니라서 제외, 다른 사람의 AWM 도 제외
 *  - 투척: 내가 2번 (다른 사람 1번, 사망 후 1번은 제외)
 */
class TelemetryAnalyzerTest {

    private final TelemetryAnalyzer analyzer = new TelemetryAnalyzer(new ObjectMapper());

    @Test
    void 주무기_보유_시간과_투척_수를_계산한다() throws IOException {
        TelemetryStats stats;
        try (InputStream in = getClass().getResourceAsStream("/pubg/telemetry.json")) {
            stats = analyzer.analyze(in, "account.test0001");
        }

        assertThat(stats.weaponSeconds())
                .containsEntry("Item_Weapon_HK416_C", 200L)
                .containsEntry("Item_Weapon_Kar98k_C", 60L)
                .containsEntry("Item_Weapon_Mini14_C", 120L)
                .doesNotContainKey("Item_Weapon_Pan_C")
                .doesNotContainKey("Item_Weapon_AWM_C");
        assertThat(stats.throwables()).isEqualTo(2);
    }

    @Test
    void 경기에_없는_사람이면_빈_기록() throws IOException {
        TelemetryStats stats;
        try (InputStream in = getClass().getResourceAsStream("/pubg/telemetry.json")) {
            stats = analyzer.analyze(in, "account.nobody");
        }

        assertThat(stats.weaponSeconds()).isEmpty();
        assertThat(stats.throwables()).isZero();
    }

    /**
     * 교전 예시 (src/test/resources/pubg/telemetry-fights.json). 우리 팀 = 1, 나 = account.me
     *
     *  14:10:00  3팀이 2팀 A1을 때림 (우리랑 상관없음)
     *  14:10:05  내가 A1을 때림 → 2팀과 새 교전, 우리가 걸기(1). A1은 5초 전 3팀에게 맞음 → 하이에나(1)
     *  14:10:20  A1이 나를 때림 → 같은 교전 (30초 안)
     *  14:11:00  3팀 B1이 팀원을 때림 → 3팀과 새 교전, 걸리기(1)
     *  14:11:10  내가 B1을 때림 → 같은 교전
     *  14:12:00  내가 B1을 때림 → 50초 지나서 새 교전, 걸기(2). B1을 직전에 때린 건 우리 팀 → 하이에나 아님
     *  14:12:30  자기장 피해 → 때린 사람 없음, 제외
     *  14:13:00~ 내가 차 탐, A1 차 탐, 14:13:10 내가 내림
     *  14:13:15  내린 지 5초, 차 탄 A1을 때림 → 해적 후보. 2팀과 새 교전, 걸기(3)
     *  14:13:30  내가 A1 기절 → 해적(1)
     *  14:13:40  걸어서 B1을 때림 → 해적 아님. 3팀과 새 교전, 걸기(4)
     *  보급상자: 77번(두 번 주움), 88번 → 2개. 팀원 99번은 제외
     *  자기장: 2.5페이즈 위치는 제외. 3페이즈에서 중심(0), 반지름 40000 중 30000 떨어짐(0.75) → 합 0.75, 2번
     *  14:17:00  내가 죽음 → 이후 팀원 교전, 보급 55번은 제외
     */
    @Test
    void 교전_해적_하이에나_보급_자기장을_계산한다() throws IOException {
        TelemetryStats stats;
        try (InputStream in = getClass().getResourceAsStream("/pubg/telemetry-fights.json")) {
            stats = analyzer.analyze(in, "account.me");
        }

        assertThat(stats.engagementsStarted()).isEqualTo(4);
        assertThat(stats.engagementsReceived()).isEqualTo(1);
        assertThat(stats.hyenaEngagements()).isEqualTo(1);
        assertThat(stats.pirateKnocks()).isEqualTo(1);
        assertThat(stats.carePackages()).isEqualTo(2);
        assertThat(stats.zoneSamples()).isEqualTo(2);
        assertThat(stats.zoneRatioSum()).isEqualTo(0.75);
    }
}
