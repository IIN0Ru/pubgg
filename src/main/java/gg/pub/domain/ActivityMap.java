package gg.pub.domain;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 활동 시간대: 요일 × 시간(한국 시간)별로 경기 수를 센 표.
 *
 * counts[요일][시] — 요일 0 = 월요일 ... 6 = 일요일, 시 0 ~ 23.
 * 화면에서는 칸마다 진하기 단계(0 ~ 4)로 칠한다.
 */
public record ActivityMap(int[][] counts, int total) {

    public static final String[] DAY_LABELS = {"월", "화", "수", "목", "금", "토", "일"};

    /** 많이 한 시간대를 말로 보여줄 때 묶는 시간 (3시간 단위) */
    static final int WINDOW_HOURS = 3;

    public static ActivityMap fromMatches(List<MatchRecord> matches) {
        int[][] counts = new int[7][24];
        int total = 0;
        for (MatchRecord m : matches) {
            if (m.playedAt() == null) {
                continue;
            }
            ZonedDateTime t = m.playedAt().atZone(MatchRecord.KOREA);
            counts[t.getDayOfWeek().getValue() - 1][t.getHour()]++;
            total++;
        }
        return new ActivityMap(counts, total);
    }

    public boolean hasData() {
        return total > 0;
    }

    private int max() {
        int max = 0;
        for (int[] day : counts) {
            for (int c : day) {
                max = Math.max(max, c);
            }
        }
        return max;
    }

    /** 칸 진하기 0 ~ 4. 가장 많이 한 칸이 4 */
    public int level(int day, int hour) {
        int c = counts[day][hour];
        if (c == 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(c * 4.0 / max()));
    }

    /** 화면용: 요일 7줄, 각 줄에 24칸 */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            List<Cell> cells = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                cells.add(new Cell(h, counts[d][h], level(d, h)));
            }
            rows.add(new Row(DAY_LABELS[d], cells));
        }
        return rows;
    }

    public record Row(String day, List<Cell> cells) {
    }

    public record Cell(int hour, int count, int level) {
        /** 마우스를 올렸을 때 보이는 설명 */
        public String title() {
            return hour + "시 · " + count + "경기";
        }
    }

    /** 주말(토·일) 경기 비율 % */
    public int weekendPercent() {
        if (total == 0) {
            return 0;
        }
        int weekend = 0;
        for (int d = DayOfWeek.SATURDAY.getValue() - 1; d < 7; d++) {
            for (int c : counts[d]) {
                weekend += c;
            }
        }
        return (int) Math.round(weekend * 100.0 / total);
    }

    /** 가장 많이 한 3시간 묶음. "21~24시". 경기가 없으면 빈 문자열 */
    public String peakText() {
        if (total == 0) {
            return "";
        }
        int[] byHour = new int[24];
        for (int[] day : counts) {
            for (int h = 0; h < 24; h++) {
                byHour[h] += day[h];
            }
        }
        // 자정을 넘는 묶음(23~2시)도 셀 수 있게 24시를 한 바퀴 돌며 센다
        int bestStart = 0;
        int best = -1;
        for (int start = 0; start < 24; start++) {
            int sum = 0;
            for (int i = 0; i < WINDOW_HOURS; i++) {
                sum += byHour[(start + i) % 24];
            }
            if (sum > best) {
                best = sum;
                bestStart = start;
            }
        }
        int end = bestStart + WINDOW_HOURS;
        return bestStart + "~" + (end > 24 ? (end - 24) : end) + "시";
    }

    /** "주로 21~24시 · 주말 40%" */
    public String summaryText() {
        return "주로 " + peakText() + " · 주말 " + weekendPercent() + "%";
    }
}
