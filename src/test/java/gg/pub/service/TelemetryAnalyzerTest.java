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

}
