package gg.pub.domain;

/**
 * 플레이 카드 칭호.
 * label 은 화면에 보이는 이름이다. 칭호 이름을 정하면 여기만 바꾸면 된다.
 */
public enum Title {

    PIRATE("해적", "차를 타고 움직이는 적을 쫓아가서 잡아요"),
    HYENA("하이에나", "다른 팀과 싸우던 적을 노려요"),
    SUPPLY_HUNTER("보급러", "보급상자를 자주 챙겨요");

    private final String label;
    private final String description;

    Title(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
