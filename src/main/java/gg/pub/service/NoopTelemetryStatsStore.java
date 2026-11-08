package gg.pub.service;

import gg.pub.domain.TelemetryStats;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 운영이 아닐 때(로컬 개발·테스트): 오래 보관하지 않는다. 메모리 캐시(@Cacheable)만 쓴다. */
@Component
@Profile("!prod")
public class NoopTelemetryStatsStore implements TelemetryStatsStore {

    @Override
    public Optional<TelemetryStats> find(String matchId, String accountId) {
        return Optional.empty();
    }

    @Override
    public void save(String matchId, String accountId, TelemetryStats stats) {
        // 저장하지 않음
    }
}
