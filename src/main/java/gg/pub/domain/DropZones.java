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
    // 아파트는 따로 된 지역 ID 가 없어서 학교(school)에 포함된다
    private static final Map<String, String> ERANGEL = Map.ofEntries(
            Map.entry("georgopol", "조지폴"),
            Map.entry("hospital", "병원"),
            Map.entry("severny", "세베르니"),
            Map.entry("kameshki", "카메시키"),
            Map.entry("yasnayapolyana", "야스나야 폴랴나"),
            Map.entry("rozhok", "로족"),
            Map.entry("school", "학교·아파트"),
            Map.entry("pochinki", "포친키"),
            Map.entry("mylta", "밀타"),
            Map.entry("primorsk", "프리모르스크"),
            Map.entry("sosnovkamilitarybase", "밀리터리 베이스"),
            Map.entry("novorepnoye", "노보레프노예"));

    // 미라마. 임팔라는 공식 목록에 "lmpala"(소문자 L)로 적혀 있어서 두 표기를 다 넣음
    private static final Map<String, String> MIRAMAR = Map.ofEntries(
            Map.entry("lacobreria", "라 코브레리아"),
            Map.entry("elpozo", "엘 포조"),
            Map.entry("sanmartin", "산 마르틴"),
            Map.entry("elazahar", "엘 아자하르"),
            Map.entry("pecado", "페카도"),
            Map.entry("montenuevo", "몬테 누에보"),
            Map.entry("lmpala", "임팔라"),
            Map.entry("impala", "임팔라"),
            Map.entry("losleones", "로스 레오네스"),
            Map.entry("valledelmar", "바예 델 마르"),
            Map.entry("chumacera", "추마세라"));

    // 태이고. 병원은 공식 지역 ID 목록에 없어서 빠짐 (좌표로 따로 넣어야 함)
    private static final Map<String, String> TAEGO = Map.ofEntries(
            Map.entry("wolsong", "월송"),
            Map.entry("godok", "고독"),
            Map.entry("armybase", "군기지"),
            Map.entry("yongcheon", "용천"),
            Map.entry("shipyard", "조선소"),
            Map.entry("palace", "궁"),
            Map.entry("hapo", "하포"),
            Map.entry("terminal", "터미널"),
            Map.entry("airport", "공항"),
            Map.entry("kangneung", "강능"),
            Map.entry("buksansa", "북산사"),
            Map.entry("hosan", "호산"),
            Map.entry("hosanprison", "호산 교도소"),
            Map.entry("school", "학교"),
            Map.entry("songam", "송암"),
            Map.entry("ohyang", "오향"));

    // 론도: 맵 이미지(Rondo_Main_Low_Res.png, 819px) 기준 픽셀 좌표와 반경
    // 겹치는 곳(네옥스 팩토리·테스트 트랙)은 위에 있는 쪽으로 판단
    private static final List<Area> RONDO = List.of(
            px("자데나 시티", 720, 585, 45),
            px("자오틴", 172, 342, 32),
            px("스타디움", 287, 248, 30),
            px("네옥스 팩토리", 485, 362, 30),
            px("테스트 트랙", 445, 367, 28),
            px("린지앙", 285, 690, 40),
            px("팀롱 가든", 505, 735, 30),
            px("무호판", 592, 158, 22),
            px("헤메이 타운", 703, 150, 28),
            px("메이란", 650, 337, 22),
            px("유린", 305, 447, 25),
            px("퐁툰", 97, 508, 25),
            px("훙산", 370, 643, 22),
            px("롱호", 147, 700, 28),
            px("단칭", 493, 517, 25));

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
