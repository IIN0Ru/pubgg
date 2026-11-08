package gg.pub.web.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON API 가 실패했을 때 돌려주는 본문. 프론트는 code 로 화면을 나누고, message 는 그대로 보여준다.
 *
 * @param code          BAD_REQUEST / PLAYER_NOT_FOUND / RATE_LIMITED / UPSTREAM_ERROR
 * @param message       사용자에게 보여줄 문장
 * @param retryAfterSec 몇 초 뒤에 다시 시도하면 되는지 (RATE_LIMITED 일 때만)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Integer retryAfterSec) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }
}
