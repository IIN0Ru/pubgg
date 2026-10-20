package gg.pub.service;

import gg.pub.pubgapi.PubgApiException;

/** 리플레이를 보여줄 수 없는 경기 (경쟁전이 아님, 지도 없음, 기록 없음 등). 메시지를 화면에 그대로 보여준다 */
public class ReplayUnavailableException extends PubgApiException {

    public ReplayUnavailableException(String message) {
        super(message);
    }
}
