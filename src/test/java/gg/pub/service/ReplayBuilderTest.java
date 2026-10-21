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
 *  - 교전 1 (170~195초): 내가 Enemy_07 기절 → 킬, Enemy_12 가 DuoFriend 기절, 215초에 내가 살림
 *  - 교전 2 (320~330초): 내가 Enemy_12 에게 기절 → 사망
 *  - 340초: 남의 팀끼리 킬 (우리 팀 아님)
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
        assertThat(me.t()).containsExactly(140, 150, 160);   // 시작 전 위치는 빠짐
        assertThat(me.x()).containsExactly(3100, 3150, 3210); // 160초는 두 번 와서 마지막 값
        assertThat(me.diedAt()).isEqualTo(330);
        assertThat(track("DuoFriend").diedAt()).isNull();
        assertThat(track("Enemy_07").diedAt()).isEqualTo(175);
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
                .containsExactly("landing", "knock", "kill", "knock", "revive", "ride", "package", "knock", "kill", "kill");
        assertThat(replay.events().get(1).text()).isEqualTo("N0-RU → Enemy_07 기절");
        assertThat(replay.events().get(5).text()).isEqualTo("DuoFriend 차량 탑승 (Mirado)");
        // 남의 팀끼리 킬, 보급상자는 우리 팀 사건이 아님
        assertThat(replay.events().get(9).ours()).isFalse();
        assertThat(replay.events().get(6).ours()).isFalse();
        // 적 착지는 아예 안 들어감
        assertThat(replay.events()).filteredOn(e -> e.type().equals("landing")).hasSize(1);
    }

    @Test
    void 교전은_30초_간격으로_묶는다() {
        assertThat(replay.fights()).hasSize(2);

        Replay.Fight first = replay.fights().get(0);
        assertThat(first.start()).isEqualTo(170);
        assertThat(first.end()).isEqualTo(195);
        assertThat(first.kills()).isEqualTo(1);
        assertThat(first.knocks()).isEqualTo(1);
        assertThat(first.knocked()).isEqualTo(1);
        assertThat(first.deaths()).isZero();

        Replay.Fight second = replay.fights().get(1);
        assertThat(second.knocked()).isEqualTo(1);
        assertThat(second.deaths()).isEqualTo(1);
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
