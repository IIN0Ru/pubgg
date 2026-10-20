package gg.pub.domain;

import java.util.Map;

/**
 * 텔레메트리 한 경기에서 뽑아낸 내 기록.
 *
 * @param weaponSeconds 주무기별 슬롯에 넣고 있던 시간 (무기 itemId → 초)
 * @param throwables    던진 투척무기 개수 (수류탄, 연막, 화염병, 섬광탄 등)
 */
public record TelemetryStats(Map<String, Long> weaponSeconds, int throwables) {
}
