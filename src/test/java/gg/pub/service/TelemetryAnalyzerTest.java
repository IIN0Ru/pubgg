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
        // 던진 종류별 횟수: 내가 던진 수류탄·연막탄만. 다른 사람이 던진 화염병은 세지 않는다
        assertThat(stats.throwableCounts())
                .containsEntry("Item_Weapon_Grenade_C", 1)
                .containsEntry("Item_Weapon_SmokeBomb_C", 1)
                .doesNotContainKey("Item_Weapon_Molotov_C");
        assertThat(stats.landing()).isNull();   // 이 예시에는 착지 기록이 없음
        assertThat(stats.geared()).isNull();    // 헬멧·가방·조끼 기록도 없음
        assertThat(stats.phase3()).isNull();    // 3페이즈 전에 죽음
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
     * 자기장 예시 (src/test/resources/pubg/telemetry-fights.json). 우리 팀 = 1, 나 = account.me
     *  (교전·차량·보급 이벤트도 들어 있지만 자기장만 센다)
     *
     *  자기장: 2페이즈에 줄어들기 시작한 건 제외.
     *    14:16:00 3페이즈 새 원(중심 200000, 반지름 40000) → 14:16:10 내 위치가 딱 중심 → 운 1.0
     *    14:16:15 파란 원이 줄기 시작 → 14:16:20 내 위치가 중심에서 30000 → 운영 0.75
     *    14:16:25 계속 줄어드는 중 → 새로 재지 않음
     *  14:17:00  내가 죽음
     */
    @Test
    void 자기장_운영과_운을_계산한다() throws IOException {
        TelemetryStats stats;
        try (InputStream in = getClass().getResourceAsStream("/pubg/telemetry-fights.json")) {
            stats = analyzer.analyze(in, "account.me");
        }

        assertThat(stats.zoneSamples()).isEqualTo(1);
        assertThat(stats.zoneRatioSum()).isEqualTo(0.75);
        assertThat(stats.zoneLuckSamples()).isEqualTo(1);
        assertThat(stats.zoneLuckSum()).isEqualTo(1.0);
    }

    /**
     * 파밍 예시 (src/test/resources/pubg/telemetry-farming.json)
     *
     *  착지: 포친키(zone "pochinki"), x 300000 / y 400000  (두 번째 착지 기록은 무시)
     *  회복·부스트: 붕대 5 + 구급상자 1 + 에너지 드링크 2 = 8 → 붕대 사용 -1, 에너지 드링크 버림 -1 = 6
     *  투척: 수류탄 1 + 연막 2 = 3 → 수류탄 던짐 -1 = 2
     *  총알: 60 + 30 = 90 주움 (버린 10은 빼지 않음: 주운 양만 셈)
     *  M416·다른 사람 의료키트는 제외, 3페이즈 이후 주운 의료키트도 제외
     *  헬멧·조끼는 포친키, 가방은 농장에서 주워서 셋이 처음 다 모인 곳 = 농장 (x 350000)
     *   - 시작 낙하산, 다른 사람 가방은 안 셈. 다 모은 뒤 학교에서 주운 2레벨 헬멧도 안 셈
     */
    @Test
    void 착지_파밍_장소와_3페이즈_가방을_기록한다() throws IOException {
        TelemetryStats stats;
        try (InputStream in = getClass().getResourceAsStream("/pubg/telemetry-farming.json")) {
            stats = analyzer.analyze(in, "account.me");
        }

        assertThat(stats.landing().zones()).containsExactly("pochinki");
        assertThat(stats.landing().x()).isEqualTo(300000);
        assertThat(stats.geared().zones()).containsExactly("farm");
        assertThat(stats.geared().x()).isEqualTo(350000);
        assertThat(stats.phase3()).isEqualTo(new TelemetryStats.Bag(6, 2, 90));
        assertThat(stats.throwables()).isEqualTo(1);
    }

    @Test
    void 안_쓰는_이벤트도_3페이즈_시작은_잡고_종류가_뒤에_나와도_읽는다() throws IOException {
        // 안 쓰는 종류(LogPlayerTakeDamage)가 3페이즈의 첫 이벤트. 칸 순서를 뒤집어서 _T 가 맨 뒤에 있는 이벤트도 섞는다
        String json = """
                [
                  {"_D":"2026-10-19T14:12:00.000Z","_T":"LogMatchStart","common":{"isGame":0}},
                  {"_D":"2026-10-19T14:13:00.000Z","_T":"LogItemPickup","common":{"isGame":1},
                   "character":{"name":"me","teamId":3,"accountId":"account.me","location":{"x":1,"y":2,"z":3},"zone":["a"]},
                   "item":{"itemId":"Item_Heal_Bandage_C","stackCount":4,"category":"Use"}},
                  {"common":{"isGame":3.0},"damage":5.5,"attacker":{"accountId":"account.other","location":{"x":9,"y":9}},
                   "_D":"2026-10-19T14:14:00.000Z","_T":"LogPlayerTakeDamage"},
                  {"item":{"itemId":"Item_Heal_Bandage_C","stackCount":2},
                   "character":{"accountId":"account.me","location":{"x":1,"y":2}},
                   "common":{"isGame":3.0},"_D":"2026-10-19T14:14:30.000Z","_T":"LogItemPickup"}
                ]
                """;

        TelemetryStats stats = analyzer.analyze(
                new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "account.me");

        // 3페이즈 시작(14:14:00) 때 붕대는 4개. 그 뒤에 주운 2개는 들어가지 않는다
        assertThat(stats.phase3()).isNotNull();
        assertThat(stats.phase3().heals()).isEqualTo(4);
    }

    @Test
    void 내가_죽은_뒤의_기록은_읽지_않는다() throws IOException {
        // 죽은 뒤에 깨진 내용이 와도 (읽지 않으므로) 오류 없이 죽을 때까지의 기록만 나온다
        String json = """
                [
                  {"_D":"2026-10-19T14:13:00.000Z","_T":"LogItemEquip","common":{"isGame":1},
                   "character":{"accountId":"account.me"},"item":{"itemId":"Item_Weapon_HK416_C","subCategory":"Main"}},
                  {"_D":"2026-10-19T14:13:50.000Z","_T":"LogPlayerKillV2","common":{"isGame":1},
                   "victim":{"accountId":"account.me"}},
                  {"_D":"2026-10-19T14:14:00.000Z","_T":"LogItemEquip", BROKEN
                """;

        TelemetryStats stats = analyzer.analyze(
                new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "account.me");

        assertThat(stats.weaponSeconds()).containsEntry("Item_Weapon_HK416_C", 50L);
    }
}

