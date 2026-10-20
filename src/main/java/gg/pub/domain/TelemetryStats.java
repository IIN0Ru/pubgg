package gg.pub.domain;

import java.util.List;
import java.util.Map;

/**
 * 텔레메트리 한 경기에서 뽑아낸 내 기록.
 *
 * @param weaponSeconds       주무기별 슬롯에 넣고 있던 시간 (무기 itemId → 초)
 * @param throwables          던진 투척무기 개수
 * @param engagementsStarted  우리 팀이 먼저 맞혀서 시작한 교전 수 (싸움걸기)
 * @param engagementsReceived 상대 팀이 먼저 맞혀서 시작한 교전 수 (싸움걸리기)
 * @param hyenaEngagements    우리가 시작한 교전 중, 상대가 직전 10초 안에 다른 팀에게 맞고 있던 경우
 * @param pirateKnocks        차에 타 있거나 내린 직후 움직이는 적을 맞히고, 그 적을 내가 기절·킬시킨 횟수
 * @param carePackages        보급상자를 연 횟수 (같은 상자는 1번)
 * @param zoneRatioSum        3페이즈부터 (자기장 중심까지 거리 ÷ 반지름) 값들의 합
 * @param zoneSamples         위 값을 잰 횟수
 * @param landing             낙하산 착지 기록 (없으면 null)
 * @param phase3              3페이즈 시작 순간의 가방 상태 (그 전에 죽었으면 null)
 */
public record TelemetryStats(Map<String, Long> weaponSeconds,
                             int throwables,
                             int engagementsStarted,
                             int engagementsReceived,
                             int hyenaEngagements,
                             int pirateKnocks,
                             int carePackages,
                             double zoneRatioSum,
                             int zoneSamples,
                             Landing landing,
                             Bag phase3) {

    /** 착지 지점: 지역 ID(zone)와 좌표(cm) */
    public record Landing(List<String> zones, double x, double y) {
    }

    /**
     * 가방 상태.
     *
     * @param heals       들고 있던 회복·부스트템 개수 (붕대, 구급상자, 의료키트, 에너지 드링크, 진통제, 아드레날린)
     * @param throwables  들고 있던 투척무기 개수
     * @param ammoPicked  그때까지 주운 총알 수 (쏜 양은 빼지 않음)
     */
    public record Bag(int heals, int throwables, int ammoPicked) {
    }

    /** 주무기·투척만 있는 기록 (테스트에서 간단히 만들 때) */
    public TelemetryStats(Map<String, Long> weaponSeconds, int throwables) {
        this(weaponSeconds, throwables, 0, 0, 0, 0, 0, 0, 0, null, null);
    }

    /** 착지·가방 정보 없이 만들 때 (테스트용) */
    public TelemetryStats(Map<String, Long> weaponSeconds, int throwables,
                          int engagementsStarted, int engagementsReceived, int hyenaEngagements,
                          int pirateKnocks, int carePackages, double zoneRatioSum, int zoneSamples) {
        this(weaponSeconds, throwables, engagementsStarted, engagementsReceived, hyenaEngagements,
                pirateKnocks, carePackages, zoneRatioSum, zoneSamples, null, null);
    }
}
