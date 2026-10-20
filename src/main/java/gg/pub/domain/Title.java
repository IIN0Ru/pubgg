package gg.pub.domain;

/**
 * 플레이 카드 칭호.
 * label 은 화면에 보이는 이름이다. 칭호 이름을 정하면 여기만 바꾸면 된다.
 *
 * criteria 는 화면에서 칭호를 누르면 보이는 "받는 기준" 문장.
 * 실제 기준 숫자는 PlayCardService 의 상수(PIRATE_MIN_COUNT 등)에 있으니, 숫자를 바꾸면 여기 문장도 같이 고친다.
 */
public enum Title {

    PIRATE("해적", "차를 타고 움직이는 적을 쫓아가서 잡아요",
            "경쟁전 10판에서 차에서 내려 바로 기절시킨 게 3번 이상"),
    HYENA("하이에나", "다른 팀과 싸우던 적을 노려요",
            "우리 팀이 건 교전 중 30% 이상이 이미 싸우던 적"),
    SUPPLY_HUNTER("보급러", "보급상자를 자주 챙겨요",
            "한 판 평균 보급상자 0.5개 이상");

    private final String label;
    private final String description;
    private final String criteria;

    Title(String label, String description, String criteria) {
        this.label = label;
        this.description = description;
        this.criteria = criteria;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getCriteria() {
        return criteria;
    }
}
