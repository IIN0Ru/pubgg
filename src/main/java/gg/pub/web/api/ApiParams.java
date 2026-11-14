package gg.pub.web.api;

import gg.pub.domain.Platform;

/** JSON API 주소 값 검사 (전적·카드 API 공통). 이상하면 BadRequestException → 400 */
final class ApiParams {

    private ApiParams() {
    }

    static Platform platform(String platform) {
        return Platform.fromShard(platform)
                .orElseThrow(() -> new BadRequestException("플랫폼은 steam 또는 kakao 만 쓸 수 있어요."));
    }

    static String nickname(String nickname) {
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("닉네임을 입력해 주세요.");
        }
        return name;
    }
}
