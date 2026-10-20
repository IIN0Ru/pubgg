package gg.pub.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자주 같이 한 팀원, 활동 시간대 계산 확인.
 *
 * 예시 경기 (최신순):
 *  | 시각(한국)        | 등수 | 팀원              |
 *  | 10/19(월) 22:10  | 1    | 친구A, 랜덤1      |
 *  | 10/18(일) 21:30  | 3    | 친구A(이름 바꾸기 전), 친구B |
 *  | 10/17(토) 23:50  | 5    | 친구B             |
 *  | 10/16(금) 14:00  | 9    | 친구A             |
 *
 *  → 친구A 3경기(치킨 1, 평균 #4.3), 친구B 2경기(평균 #4.0), 랜덤1 1경기라 제외
 */
class TeammateAndActivityTest {

    private static MatchPlayer p(String id, String name) {
        return new MatchPlayer(name, id, 0, 0, 0, 0, 0, 0, 0, 0, 0, "alive");
    }

    private static MatchRecord m(String utc, int rank, MatchPlayer... mates) {
        MatchPlayer me = new MatchPlayer("N0-RU", "me", 0, 0, 0, 0, rank, 0, 0, 0, 0, "alive");
        return new MatchRecord("x", Instant.parse(utc), "squad-fpp", "Baltic_Main", "official", 16, null,
                me, List.of(mates));
    }

    private final List<MatchRecord> matches = List.of(
            m("2026-10-19T13:10:00Z", 1, p("a", "친구A"), p("r", "랜덤1")),
            m("2026-10-18T12:30:00Z", 3, p("a", "옛이름A"), p("b", "친구B")),
            m("2026-10-17T14:50:00Z", 5, p("b", "친구B")),
            m("2026-10-16T05:00:00Z", 9, p("a", "옛이름A")));

    @Test
    void 두_판_이상_같이_한_팀원을_많이_한_순서로() {
        List<Teammate> mates = Teammate.fromMatches(matches, 5);

        assertThat(mates).extracting(Teammate::name).containsExactly("친구A", "친구B");
        Teammate a = mates.get(0);
        assertThat(a.games()).isEqualTo(3);
        assertThat(a.wins()).isEqualTo(1);
        assertThat(a.avgRankText()).isEqualTo("#4.3");   // (1 + 3 + 9) / 3
        assertThat(mates.get(1).avgRankText()).isEqualTo("#4.0");
    }

    @Test
    void 활동_시간대는_한국_시간_요일과_시로_센다() {
        ActivityMap act = ActivityMap.fromMatches(matches);

        assertThat(act.total()).isEqualTo(4);
        assertThat(act.counts()[0][22]).isEqualTo(1);   // 월 22시
        assertThat(act.counts()[6][21]).isEqualTo(1);   // 일 21시
        assertThat(act.counts()[5][23]).isEqualTo(1);   // 토 23시
        assertThat(act.counts()[4][14]).isEqualTo(1);   // 금 14시
        assertThat(act.level(0, 22)).isEqualTo(4);
        assertThat(act.level(0, 0)).isZero();
        assertThat(act.weekendPercent()).isEqualTo(50);
        assertThat(act.peakText()).isEqualTo("21~24시");  // 21, 22, 23시에 3판
        assertThat(act.rows()).hasSize(7);
        assertThat(act.rows().get(0).cells()).hasSize(24);
    }

    @Test
    void 자정을_넘는_시간대도_찾는다() {
        ActivityMap act = ActivityMap.fromMatches(List.of(
                m("2026-10-19T15:10:00Z", 5), m("2026-10-19T16:10:00Z", 5), m("2026-10-19T17:10:00Z", 5)));

        assertThat(act.peakText()).isEqualTo("0~3시");   // 한국 0, 1, 2시
    }
}
