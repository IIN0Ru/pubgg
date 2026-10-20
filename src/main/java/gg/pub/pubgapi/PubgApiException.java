package gg.pub.pubgapi;

/**
 * PUBG API 호출이 실패했을 때 던지는 예외.
 * message 는 화면에 그대로 보여줄 수 있는 문장으로 만든다.
 */
public class PubgApiException extends RuntimeException {

    public PubgApiException(String message) {
        super(message);
    }

    public PubgApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
