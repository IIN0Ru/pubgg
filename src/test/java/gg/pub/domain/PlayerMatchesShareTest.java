package gg.pub.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 링크 미리보기 문구 (디스코드에 링크를 붙였을 때 보이는 제목·설명) */
class PlayerMatchesShareTest {

    private static MatchRecord match(String utc) {
        MatchPlayer me = new MatchPlayer("N0-RU", "me", 0, 0, 0, 0, 5, 0, 0, 0, 0, "alive");
        return new MatchRecord("x", Instant.parse(utc), "squad-fpp", "Baltic_Main", "competitive", 16, null, me, List.of());
    }

    @Test
    void 티어와_핵심_수치_스타일_활동시간을_한_줄로() {
        Farming farming = new Farming(Farming.BIG_CITY, "포친키", 7, 10, 8, 7.5, 3.2, 214);
        PlayCard card = new PlayCard(10, 20, 6, 8, 412, "FPP", List.of(), 2.0, 10,
                List.of(Title.PIRATE), 38, List.of(38), 62, List.of(62), farming);
        SeasonRank season = new SeasonRank("squad-fpp", "Platinum", "3", 2450, 120, 2.76, 330, 7.4, 61);
        // 한국 시간 22시 경기 3판 → 22시가 가운데인 21~24시
        List<MatchRecord> matches = List.of(match("2026-10-19T13:10:00Z"), match("2026-10-18T13:30:00Z"),
                match("2026-10-17T13:50:00Z"));

        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, card, season, matches);

        assertThat(result.shareTitle()).isEqualTo("N0-RU · 플래티넘 3");
        assertThat(result.shareDescription())
                .isEqualTo("KDA 3.25 · 평균 딜 412 · 해적 · 센터 운영 · 대도시파 · 주로 21~24시");
    }

    @Test
    void 기록이_없으면_기본_문구() {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());

        assertThat(result.shareTitle()).isEqualTo("N0-RU");
        assertThat(result.shareDescription()).isEqualTo("스팀 N0-RU의 배그 플레이 스타일");
    }
}
