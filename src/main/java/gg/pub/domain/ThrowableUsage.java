package gg.pub.domain;

import java.util.Locale;

/**
 * 가장 많이 던진 투척무기와, 그걸 한 판에 평균 몇 번 던졌는지.
 *
 * @param name     "수류탄", "연막탄" 처럼 화면에 보여줄 이름
 * @param perMatch 한 판 평균 던진 횟수
 */
public record ThrowableUsage(String name, double perMatch) {

    /** "1.1" */
    public String perMatchText() {
        return String.format(Locale.ROOT, "%.1f", perMatch);
    }
}
