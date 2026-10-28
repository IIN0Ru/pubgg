package gg.pub.card;

/** 다른 브라우저가 처음 저장한 카드라 덮어쓸 수 없을 때 */
public class CardLockedException extends RuntimeException {

    public CardLockedException() {
        super("다른 브라우저에서 처음 저장한 카드라 여기서는 바꿀 수 없어요.");
    }
}
