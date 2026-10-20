package gg.pub.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 자주 같이 한 팀원 한 명.
 *
 * @param name    닉네임 (가장 최근 경기의 이름)
 * @param games   같이 한 경기 수
 * @param wins    같이 먹은 치킨 수
 * @param avgRank 같이 한 경기 평균 등수
 */
public record Teammate(String name, int games, int wins, double avgRank) {

    /** 2판 이상 같이 한 사람만 "자주 같이 한 팀원"으로 본다 (한 판은 랜덤 매칭일 수 있어서) */
    public static final int MIN_GAMES = 2;

    /** "#4.3" */
    public String avgRankText() {
        return "#" + String.format(Locale.ROOT, "%.1f", avgRank);
    }

    /**
     * 경기들에서 자주 같이 한 팀원을 뽑는다.
     * 닉네임은 바뀔 수 있어서 계정 ID 로 묶는다. 같이 한 판이 많은 순, 같으면 평균 등수가 좋은 순.
     *
     * @param matches 최신순 경기
     * @param limit   최대 몇 명
     */
    public static List<Teammate> fromMatches(List<MatchRecord> matches, int limit) {
        Map<String, String> nameById = new LinkedHashMap<>();
        Map<String, List<Integer>> ranksById = new HashMap<>();

        for (MatchRecord m : matches) {
            for (MatchPlayer t : m.teammates()) {
                if (t.accountId() == null) {
                    continue;
                }
                nameById.putIfAbsent(t.accountId(), t.name());   // 최신순이라 처음 본 이름이 가장 최근 이름
                ranksById.computeIfAbsent(t.accountId(), k -> new ArrayList<>()).add(m.rank());
            }
        }

        return nameById.entrySet().stream()
                .map(e -> {
                    List<Integer> ranks = ranksById.get(e.getKey());
                    int wins = (int) ranks.stream().filter(r -> r == 1).count();
                    double avg = ranks.stream().mapToInt(Integer::intValue).average().orElse(0);
                    return new Teammate(e.getValue(), ranks.size(), wins, avg);
                })
                .filter(t -> t.games() >= MIN_GAMES)
                .sorted((a, b) -> a.games() != b.games()
                        ? Integer.compare(b.games(), a.games())
                        : Double.compare(a.avgRank(), b.avgRank()))
                .limit(limit)
                .toList();
    }
}
