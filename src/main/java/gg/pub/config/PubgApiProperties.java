package gg.pub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties 의 pubg.api.* 값을 담는 설정 클래스.
 *
 * key 는 코드에 직접 쓰지 않고, 컴퓨터의 환경 변수 PUBG_API_KEY 에서 읽어온다.
 * (application.properties 에 pubg.api.key=${PUBG_API_KEY:} 로 연결되어 있다)
 *
 * @param key              PUBG API 키
 * @param baseUrl          API 기본 주소 (https://api.pubg.com)
 * @param recentMatchLimit 최근 경기를 몇 개까지 가져올지
 */
@ConfigurationProperties(prefix = "pubg.api")
public record PubgApiProperties(String key, String baseUrl, int recentMatchLimit) {

    public boolean hasKey() {
        return key != null && !key.isBlank();
    }
}
