package gg.pub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.replay.MapInfo;
import gg.pub.domain.replay.Replay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리플레이 데이터 추리기 확인 (src/test/resources/pubg/telemetry-replay.json).
 *
 * 예시 경기: 14:10:00 시작. 나(N0-RU)와 DuoFriend 가 1팀.
 *  - 130초 착지, 140~160초 이동 (160초에는 위치가 두 번 와서 마지막 값)
 *  - 교전 1 (170~195초): 내가 Enemy_07 수류탄으로 기절 → 킬, Enemy_12 가 DuoFriend 기절, 215초에 내가 살림
 *  - 교전 2 (320~330초): 내가 Enemy_12 에게 기절 → 화염병으로 사망
 *  - 150초 위치: DuoFriend 는 차량, Enemy_07 은 보트. 140초 내 위치는 비행기(→ 걸어서로 봄)
 *  - 340초: 남의 팀끼리 킬 (우리 팀 아님)
 *  - 164초 내가 수류탄 던짐(attackId 501) → 169초 Enemy_07(3300,3200)·Enemy_08(3340,3240)·나 자신이 맞음
 *    → 터진 곳 = 적 둘 가운데 (3320,3220), 던진 곳 (3200,3150), 170초 기절로 이어져 knock
 *  - 270초 DuoFriend 박격포 명중(hit), 280초 판처파우스트 명중 → 281초 Enemy_13 기절 (판처파우스트)
 *  - 370초 DuoFriend 가 프라이팬을 던져 Enemy_14 기절 → 근접은 무기 이름을 안 붙임
 *  - 회복: 178초 나 에너지 드링크, 220초 DuoFriend 구급상자 (적 붕대·내 조끼는 제외)
 *  - 비행기 낙하 60·90·120초 → 경로 (1000,1000) → (6000,6000)
 *  - 다음 안전구역이 200초, 400초에 작아짐 → 페이즈 1, 2
 *  - 피해 6건 중 리플레이 명중선은 2건: 168초 내가 Enemy_07 을 맞힘, 190초 Enemy_12 가 DuoFriend 를 맞힘
 *    (자기장·수류탄 피해, 남의 팀끼리, 우리끼리 오사는 뺀다)
 */
class ReplayBuilderTest {

    private Replay replay;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/pubg/telemetry-replay.json")) {
            replay = new ReplayBuilder(new ObjectMapper())
                    .build(in, "m1", MapInfo.of("Baltic_Main").orElseThrow(), "account.me");
        }
    }

    private Replay.Track track(String name) {
        return replay.players().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void 경기_시작부터_초_단위_미터_단위로_줄인다() {
        assertThat(replay.myTeam()).isEqualTo(1);
        assertThat(replay.duration()).isEqualTo(600);

        Replay.Track me = track("N0-RU");
        assertThat(me.t()).startsWith(140, 150, 160);   // 시작 전 위치는 빠짐 (그 뒤는 피해·기절 순간 위치)
        assertThat(me.x()).startsWith(3100, 3150, 3210); // 160초는 두 번 와서 마지막 값
        assertThat(me.diedAt()).isEqualTo(330);
        assertThat(track("DuoFriend").diedAt()).isNull();
        assertThat(track("Enemy_07").diedAt()).isEqualTo(175);
    }

    @Test
    void 차량_보트를_탄_순간을_기록한다() {
        assertThat(track("DuoFriend").v()).startsWith(0, 1, 0);
        assertThat(track("Enemy_07").v()).startsWith(0, 2, 0);
        assertThat(track("N0-RU").v()).containsOnly(0);   // 비행기는 차량 아이콘으로 그리지 않음
    }

    @Test
    void 공격_투척으로_기절_킬하면_무엇으로_했는지() {
        Replay.Event grenadeKnock = replay.events().get(1);
        assertThat(grenadeKnock.cause()).isEqualTo("grenade");
        assertThat(replay.events().get(2).cause()).isNull();          // 175초 킬은 총
        Replay.Event molotovKill = replay.events().get(9);
        assertThat(molotovKill.type()).isEqualTo("kill");
        assertThat(molotovKill.cause()).isEqualTo("molotov");
        assertThat(molotovKill.text()).isEqualTo("Enemy_12 → N0-RU 킬 (화염병)");
    }

    @Test
    void 판처는_무기_이름을_붙이고_근접은_안_붙인다() {
        assertThat(replay.events().get(6).cause()).isEqualTo("panzer");
        assertThat(replay.events().get(6).text()).isEqualTo("DuoFriend → Enemy_13 기절 (판처파우스트)");
        assertThat(replay.events().get(11).cause()).isNull();
        assertThat(replay.events().get(11).text()).isEqualTo("DuoFriend → Enemy_14 기절");
    }

    @Test
    void 폭발물은_맞은_적들_가운데를_터진_곳으로() {
        assertThat(replay.blasts()).hasSize(3);

        Replay.Blast grenade = replay.blasts().get(0);
        assertThat(grenade.kind()).isEqualTo("grenade");
        assertThat(grenade.t()).isEqualTo(169);
        assertThat(grenade.fromX()).isEqualTo(3200);   // 던진 곳 (attackId 로 이음)
        assertThat(grenade.fromY()).isEqualTo(3150);
        assertThat(grenade.x()).isEqualTo(3320);       // 적 둘 가운데. 나 자신 피해는 안 셈
        assertThat(grenade.y()).isEqualTo(3220);
        assertThat(grenade.victims()).isEqualTo(2);
        assertThat(grenade.result()).isEqualTo("knock");

        assertThat(replay.blasts().get(1).kind()).isEqualTo("mortar");
        assertThat(replay.blasts().get(1).result()).isEqualTo("hit");
        assertThat(replay.blasts().get(2).kind()).isEqualTo("panzer");
        assertThat(replay.blasts().get(2).result()).isEqualTo("knock");
    }

    @Test
    void 우리_팀_회복템만() {
        assertThat(replay.heals()).containsExactly(
                new Replay.Heal(178, "N0-RU", 3200, 3150, "에너지 드링크"),
                new Replay.Heal(220, "DuoFriend", 3220, 3150, "구급상자"));
    }

    @Test
    void 킬_글에_총_이름_거리_헤드샷() {
        assertThat(replay.events().get(2).text()).isEqualTo("N0-RU → Enemy_07 킬 (M416 · 15m · 헤드샷)");
    }

    @Test
    void 체력은_위치_기록에서_피해를_뺀다() {
        Replay.Track me = track("N0-RU");
        int at169 = indexOf(me.t(), 169);   // 내 수류탄에 5 맞음
        assertThat(me.hp()[at169]).isEqualTo(95);
        int at192 = indexOf(me.t(), 192);   // 자기장 2 + 적 수류탄 40
        assertThat(me.hp()[at192]).isEqualTo(53);
        int at320 = indexOf(me.t(), 320);   // 기절 중은 0
        assertThat(me.hp()[at320]).isZero();
    }

    private static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        throw new AssertionError(value + " 없음");
    }

    @Test
    void 우리_팀이_받은_피해_한_줄씩() {
        // 190초 Enemy_12 가 DuoFriend 를 Beryl 로 머리에 맞힘. 15m 위, 187m 거리
        Replay.Hurt h = replay.hurts().stream().filter(x -> x.t() == 190).findFirst().orElseThrow();
        assertThat(h.victim()).isEqualTo("DuoFriend");
        assertThat(h.attacker()).isEqualTo("Enemy_12");
        assertThat(h.weapon()).isEqualTo("Beryl");
        assertThat(h.damage()).isEqualTo(25);
        assertThat(h.hp()).isEqualTo(75);
        assertThat(h.distance()).isEqualTo(187);
        assertThat(h.height()).isEqualTo(15);
        assertThat(h.headshot()).isTrue();
        // 자기장 피해는 때린 사람 없이
        assertThat(replay.hurts()).anyMatch(x -> x.weapon().equals("자기장") && x.attacker() == null);
        // 적끼리 피해는 안 들어감
        assertThat(replay.hurts()).noneMatch(x -> x.victim().equals("Other2"));
    }

    @Test
    void 교전_지표() {
        Replay.Fight first = replay.fights().get(0);
        assertThat(first.enemyTeam()).isEqualTo(2);
        assertThat(first.converted()).isEqualTo(1);   // Enemy_07 기절 → 내가 킬
        assertThat(first.losses()).isEqualTo(1);      // DuoFriend 기절
        assertThat(first.traded()).isZero();          // Enemy_12 를 10초 안에 못 잡음
        assertThat(first.revived()).isEqualTo(1);     // 215초에 살림

        Replay.Fight third = replay.fights().get(2);  // 나 기절 → 사망: 당한 건 기절 한 번으로 셈
        assertThat(third.losses()).isEqualTo(1);
        assertThat(third.revived()).isZero();
    }

    @Test
    void 비행기_경로와_페이즈() {
        assertThat(replay.plane()).isEqualTo(new Replay.Plane(1000, 1000, 6000, 6000));
        assertThat(replay.phases()).containsExactly(new Replay.Phase(1, 200), new Replay.Phase(2, 400));
        assertThat(replay.zones()).hasSize(4);
        assertThat(replay.zones().get(1).nextR()).isEqualTo(2000);   // 200000cm
    }

    @Test
    void 우리_팀_사건만_ours_로_표시한다() {
        assertThat(replay.events()).extracting(Replay.Event::type)
                .containsExactly("landing", "knock", "kill", "knock", "revive", "ride", "knock", "package", "knock", "kill",
                        "kill", "knock");
        assertThat(replay.events().get(1).text()).isEqualTo("N0-RU → Enemy_07 기절 (수류탄 · 12m)");
        assertThat(replay.events().get(5).text()).isEqualTo("DuoFriend 차량 탑승 (Mirado)");
        // 남의 팀끼리 킬, 보급상자는 우리 팀 사건이 아님
        assertThat(replay.events().get(10).ours()).isFalse();
        assertThat(replay.events().get(7).ours()).isFalse();
        // 적 착지는 아예 안 들어감
        assertThat(replay.events()).filteredOn(e -> e.type().equals("landing")).hasSize(1);
    }

    @Test
    void 교전은_30초_간격으로_묶는다() {
        assertThat(replay.fights()).hasSize(4);   // 170~195, 281(판처), 320~330, 370(프라이팬)

        Replay.Fight first = replay.fights().get(0);
        assertThat(first.start()).isEqualTo(170);
        assertThat(first.end()).isEqualTo(195);
        assertThat(first.kills()).isEqualTo(1);
        assertThat(first.knocks()).isEqualTo(1);
        assertThat(first.knocked()).isEqualTo(1);
        assertThat(first.deaths()).isZero();

        Replay.Fight third = replay.fights().get(2);
        assertThat(third.knocked()).isEqualTo(1);
        assertThat(third.deaths()).isEqualTo(1);
    }

    @Test
    void 총_명중은_우리_팀이_맞힌_것과_우리가_맞은_것만() {
        assertThat(replay.hits()).hasSize(2);

        Replay.Hit mine = replay.hits().get(0);
        assertThat(mine.t()).isEqualTo(168);
        assertThat(mine.ours()).isTrue();
        assertThat(mine.attacker()).isEqualTo("N0-RU");
        assertThat(mine.victim()).isEqualTo("Enemy_07");
        assertThat(mine.ax()).isEqualTo(3200);
        assertThat(mine.vx()).isEqualTo(3300);

        Replay.Hit onUs = replay.hits().get(1);
        assertThat(onUs.ours()).isFalse();
        assertThat(onUs.attacker()).isEqualTo("Enemy_12");
        assertThat(onUs.victim()).isEqualTo("DuoFriend");
    }

    @Test
    void 경쟁전_맵만_지도가_있다() {
        assertThat(MapInfo.of("Tiger_Main")).isPresent();
        assertThat(MapInfo.of("Savage_Main")).isEmpty();
        assertThat(MapInfo.of("Baltic_Main").orElseThrow().size()).isEqualTo(8_160);
    }
}
