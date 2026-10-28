package gg.pub.domain;

/**
 * 클랜. 플레이어 카드 닉네임 위에 "[HLMT] 헬멧연구소" 로 보여준다.
 *
 * @param name 클랜 이름
 * @param tag  클랜 태그 (짧은 영문, 없을 수 있음)
 */
public record Clan(String name, String tag) {

    /** "[HLMT] 헬멧연구소". 태그가 없으면 이름만 */
    public String label() {
        if (tag == null || tag.isBlank()) {
            return name == null ? "" : name;
        }
        return "[" + tag + "] " + (name == null ? "" : name);
    }
}
