package gg.pub.domain;

import java.util.List;
import java.util.Locale;

/**
 * 플레이 카드의 기본 정보. 경쟁전 최근 경기들을 모아서 계산한다.
 *
 * @param matches         계산에 쓴 경쟁전 경기 수 (최대 10)
 * @param kills           총 킬
 * @param assists         총 어시스트
 * @param deaths          죽은 판 수 (1등이 아닌 판)
 * @param avgDamage       평균 딜량
 * @param perspective     "FPP" 또는 "TPP" (더 많이 한 쪽)
 * @param mainWeapons     슬롯에 가장 오래 넣고 있던 주무기 (최대 2개)
 * @param avgThrowables   한 판 평균 투척 개수
 * @param analyzedMatches 텔레메트리 분석에 성공한 경기 수 (주무기·투척은 이 경기들 기준)
 */
public record PlayCard(int matches,
                       int kills,
                       int assists,
                       int deaths,
                       int avgDamage,
                       String perspective,
                       List<WeaponUsage> mainWeapons,
                       double avgThrowables,
                       int analyzedMatches) {

    /** KDA = (킬 + 어시스트) ÷ 죽은 판 수. 한 번도 안 죽었으면 (킬 + 어시스트) 그대로 */
    public double kda() {
        int ka = kills + assists;
        return deaths == 0 ? ka : (double) ka / deaths;
    }

    /** "3.42" */
    public String kdaText() {
        return String.format(Locale.ROOT, "%.2f", kda());
    }

    /** "2.3" */
    public String avgThrowablesText() {
        return String.format(Locale.ROOT, "%.1f", avgThrowables);
    }

    public boolean hasWeapons() {
        return !mainWeapons.isEmpty();
    }
}
