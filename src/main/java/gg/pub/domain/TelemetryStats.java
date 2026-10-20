package gg.pub.domain;

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
 */
public record TelemetryStats(Map<String, Long> weaponSeconds,
                             int throwables,
                             int engagementsStarted,
                             int engagementsReceived,
                             int hyenaEngagements,
                             int pirateKnocks,
                             int carePackages,
                             double zoneRatioSum,
                             int zoneSamples) {

    /** 주무기·투척만 있는 기록 (테스트에서 간단히 만들 때) */
    public TelemetryStats(Map<String, Long> weaponSeconds, int throwables) {
        this(weaponSeconds, throwables, 0, 0, 0, 0, 0, 0, 0);
    }
}
