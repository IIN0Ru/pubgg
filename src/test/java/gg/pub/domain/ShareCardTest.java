package gg.pub.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 공유용 플레이어 카드 (/card) 에 들어가는 값 */
class ShareCardTest {

    /** kills·헤드샷·등수·맵만 다른 경쟁전 한 판 */
    private static MatchRecord game(String map, int kills, int heads, int rank) {
        MatchPlayer me = new MatchPlayer("N0-RU", "me", kills, 0, 0, 0, rank, 0, heads, 0, 0, "alive");
        return new MatchRecord("x", Instant.parse("2026-10-19T13:10:00Z"), "squad-fpp", map, "competitive",
                16, null, me, List.of());
    }

    private static PlayCard card(int matches) {
        Farming farming = new Farming(Farming.BIG_CITY, "포친키", 7, 10, 8, 7.5, 3.2, 214);
        return new PlayCard(matches, 20, 6, 8, 412, "FPP",
                List.of(new WeaponUsage("Beryl", WeaponClass.AR_762, 60)), 2.1, matches,
                62, List.of(62), 50, List.of(50), farming);
    }

    @Test
    void 기록으로_카드_값을_만든다() {
        List<MatchRecord> games = List.of(
                game("Desert_Main", 4, 2, 3),
                game("Desert_Main", 2, 0, 15),
                game("Baltic_Main", 4, 1, 1),
                game("Tiger_Main", 0, 0, 30));
        SeasonRank season = new SeasonRank("squad-fpp", "Diamond", "2", 3100, 80, 3.0, 400, 6.0, 60);
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, card(4), season, games,
                new Clan("헬멧연구소", "HLMT"));

        ShareCard c = ShareCard.from(result).orElseThrow();

        assertThat(c.clan()).isEqualTo("[HLMT] 헬멧연구소");
        assertThat(c.tierFamily()).isEqualTo("Diamond");
        assertThat(c.tierIcon()).isEqualTo("Diamond-2");
        assertThat(c.tierText()).isEqualTo("DIAMOND II");
        assertThat(c.kda()).isEqualTo("3.25");
        assertThat(c.weaponName()).isEqualTo("Beryl");
        assertThat(c.weaponClass()).isEqualTo("AR_762");
        // 헤드샷 3/10 = 30%, TOP10 2/4 = 50%
        assertThat(c.combatTags()).containsExactly("외곽 운영", "대도시파", "헤드샷 30%", "TOP10 50%", "투척 2.1개");
        assertThat(c.mapKey()).isEqualTo("miramar");
        assertThat(c.matches()).isEqualTo(4);
        assertThat(c.peakHours()).isEqualTo("21~24시");
    }

    @Test
    void 경쟁전_카드가_없으면_빈_값() {
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, null, List.of());
        assertThat(ShareCard.from(result)).isEmpty();
    }

    @Test
    void 일반전은_빼고_카드와_같은_경쟁전만_센다() {
        MatchPlayer me = new MatchPlayer("N0-RU", "me", 10, 0, 0, 0, 1, 0, 10, 0, 0, "alive");
        MatchRecord normal = new MatchRecord("n", Instant.parse("2026-10-19T14:00:00Z"), "squad-fpp", "Baltic_Main",
                "official", 16, null, me, List.of());
        List<MatchRecord> games = List.of(normal, game("Neon_Main", 2, 1, 20));
        PlayerMatches result = new PlayerMatches("N0-RU", Platform.STEAM, card(1), null, games);

        ShareCard c = ShareCard.from(result).orElseThrow();

        assertThat(c.combatTags()).contains("헤드샷 50%", "TOP10 0%");
        assertThat(c.mapKey()).isEqualTo("rondo");
    }

    @Test
    void 맵이_같은_수면_최근_맵_이미지가_없는_맵은_에란겔() {
        assertThat(ShareCard.mainMap(List.of(game("Tiger_Main", 0, 0, 1), game("Desert_Main", 0, 0, 1))))
                .isEqualTo("taego");
        assertThat(ShareCard.mainMap(List.of(game("Savage_Main", 0, 0, 1)))).isEqualTo("erangel");
        assertThat(ShareCard.mainMap(List.of())).isEqualTo("erangel");
    }

    @Test
    void 티어_글자와_아이콘() {
        assertThat(ShareCard.tier(null)).containsExactly("Unranked", "Unranked", "UNRANKED");
        SeasonRank master = new SeasonRank("squad", "Master", "1", 4000, 10, 1, 1, 1, 1);
        assertThat(ShareCard.tier(master)).containsExactly("Master", "Master", "MASTER");
        SeasonRank crystal = new SeasonRank("squad", "Crystal", "5", 2900, 10, 1, 1, 1, 1);
        assertThat(ShareCard.tier(crystal)).containsExactly("Crystal", "Crystal-5", "CRYSTAL V");
        SeasonRank placing = new SeasonRank("squad", "Unranked", null, 0, 3, 1, 1, 1, 1);
        assertThat(ShareCard.tier(placing)[0]).isEqualTo("Unranked");
    }

    @Test
    void 클랜_태그가_없으면_이름만() {
        assertThat(new Clan("헬멧연구소", "").label()).isEqualTo("헬멧연구소");
        assertThat(new Clan("헬멧연구소", "HLMT").label()).isEqualTo("[HLMT] 헬멧연구소");
    }
}
