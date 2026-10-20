package gg.pub.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * @Cacheable 을 켠다. (경기 기록 MatchRecordService, 텔레메트리 분석 TelemetryService)
 *
 * 메인 클래스가 아니라 따로 둔 이유: 컨트롤러만 띄우는 테스트(@WebMvcTest)에서는
 * 이 설정이 빠지도록 하기 위해서.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
