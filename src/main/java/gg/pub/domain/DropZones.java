package gg.pub.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 랭크(경쟁전) 맵의 "대도시" 목록과 착지 지점 판단.
 *
 * 1) 에란겔·미라마·태이고: 텔레메트리 Character 의 zone(지역 ID)으로 판단한다.
 *    지역 ID 출처: github.com/pubg/api-assets (enums/telemetry/regionId.json)
 * 2) 론도: 지역 ID 목록이 없어서, 공식 맵 이미지(지명 포함)에서 도시 위치를 읽어 좌표 원으로 판단한다.
 *    이미지 819px = 맵 816,000cm → 1px ≈ 996cm(약 10m). 맵 크기 8km는 확인 필요.
 *
 * 어디를 대도시로 볼지는 직접 정했다. 바꾸려면 여기 목록만 고치면 된다.
 */
public final class DropZones {

    private DropZones() {
    }

    /** 원 모양 도시 범위 (cm) */
    record Area(String name, double x, double y, double radius) {
        boolean contains(double px, double py) {
            return Math.hypot(px - x, py - y) <= radius;
        }
    }

    private static final double CM_PER_PX = 816_000.0 / 819;

    private static Area px(String name, double x, double y, double r) {
        return new Area(name, x * CM_PER_PX, y * CM_PER_PX, r * CM_PER_PX);
    }

    // 에란겔 (리마스터 Baltic_Main, 옛 Erangel_Main 같은 지역 ID 사용)
    private static final Map<String, String> ERANGEL = Map.of(
            "pochinki", "포친키",
            "school", "학교·아파트",
            "rozhok", "로족",
            "yasnayapolyana", "야스나야 폴랴나",
            "georgopol", "조지폴",
            "sosnovkamilitarybase", "밀리터리 베이스",
            "mylta", "밀타");

    // 미라마
    private static final Map<String, String> MIRAMAR = Map.of(
            "losleones", "로스 레오네스",
            "pecado", "페카도",
            "elpozo", "엘 포조",
            "sanmartin", "산 마르틴",
            "heciendadelpatron", "아시엔다 델 파트론",
            "montenuevo", "몬테 누에보");

    // 태이고
    private static final Map<String, String> TAEGO = Map.of(
            "hosan", "호산",
            "godok", "고독",
            "terminal", "터미널",
            "kangneung", "강능",
            "yongcheon", "용천",
            "airport", "공항");

    // 론도: 맵 이미지(Rondo_Main_Low_Res.png, 819px) 기준 픽셀 좌표와 반경
    private static final List<Area> RONDO = List.of(
            px("자데나 시티", 720, 585, 45),
            px("자오틴", 172, 342, 32),
            px("스타디움", 287, 248, 30),
            px("네옥스 팩토리", 485, 362, 30),
            px("린지앙", 285, 690, 40),
            px("팀롱 가든", 505, 735, 30));

    /**
     * 착지 지점이 대도시면 도시 이름을, 아니면 빈 값.
     *
     * @param mapName 경기 맵 (Baltic_Main, Desert_Main, Tiger_Main, Neon_Main ...)
     * @param zones   착지 순간 Character.zone (지역 ID들)
     * @param x       착지 x 좌표 (cm)
     * @param y       착지 y 좌표 (cm)
     */
    public static Optional<String> bigCity(String mapName, List<String> zones, double x, double y) {
        if (mapName == null) {
            return Optional.empty();
        }
        return switch (mapName) {
            case "Baltic_Main", "Erangel_Main" -> byZone(ERANGEL, zones);
            case "Desert_Main" -> byZone(MIRAMAR, zones);
            case "Tiger_Main" -> byZone(TAEGO, zones);
            case "Neon_Main" -> RONDO.stream().filter(a -> a.contains(x, y)).map(Area::name).findFirst();
            default -> Optional.empty();   // 랭크 맵이 아니면 판단하지 않음
        };
    }

    private static Optional<String> byZone(Map<String, String> cities, List<String> zones) {
        if (zones == null) {
            return Optional.empty();
        }
        return zones.stream()
                .map(z -> cities.get(z.toLowerCase()))
                .filter(name -> name != null)
                .findFirst();
    }
}
