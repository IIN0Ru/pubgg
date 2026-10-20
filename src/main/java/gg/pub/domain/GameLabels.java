package gg.pub.domain;

import java.util.Map;

/**
 * PUBG API 가 주는 내부 이름을 화면에 보여줄 한글 이름으로 바꾼다.
 * 맵 이름 출처: github.com/pubg/api-assets (dictionaries/telemetry/mapName.json)
 * 모르는 값이 오면 원래 값을 그대로 보여준다.
 */
public final class GameLabels {

    private GameLabels() {
    }

    private static final Map<String, String> MAPS = Map.ofEntries(
            Map.entry("Baltic_Main", "에란겔"),
            Map.entry("Erangel_Main", "에란겔"),
            Map.entry("Desert_Main", "미라마"),
            Map.entry("Savage_Main", "사녹"),
            Map.entry("DihorOtok_Main", "비켄디"),
            Map.entry("Summerland_Main", "카라킨"),
            Map.entry("Tiger_Main", "태이고"),
            Map.entry("Kiki_Main", "데스턴"),
            Map.entry("Neon_Main", "론도"),
            Map.entry("Chimera_Main", "파라모"),
            Map.entry("Heaven_Main", "헤이븐"),
            Map.entry("Range_Main", "캠프 자칼")
    );

    private static final Map<String, String> MATCH_TYPES = Map.of(
            "official", "일반",
            "competitive", "경쟁",
            "custom", "사용자 지정",
            "event", "이벤트",
            "arcade", "아케이드",
            "training", "훈련",
            "airoyale", "캐주얼"
    );

    public static String map(String mapName) {
        if (mapName == null) {
            return "알 수 없음";
        }
        return MAPS.getOrDefault(mapName, mapName);
    }

    public static String matchType(String matchType) {
        if (matchType == null) {
            return "";
        }
        return MATCH_TYPES.getOrDefault(matchType, matchType);
    }

    /**
     * "squad-fpp" → "스쿼드 FPP", "normal-duo" → "듀오 TPP", "tdm" → "팀 데스매치"
     */
    public static String gameMode(String gameMode) {
        if (gameMode == null) {
            return "알 수 없음";
        }
        if (gameMode.equals("tdm")) {
            return "팀 데스매치";
        }

        String perspective = gameMode.endsWith("-fpp") ? "FPP" : "TPP";
        String base = gameMode.replace("-fpp", "").replace("normal-", "");

        String size = switch (base) {
            case "solo" -> "솔로";
            case "duo" -> "듀오";
            case "squad" -> "스쿼드";
            default -> null;
        };
        return size == null ? gameMode : size + " " + perspective;
    }
}
