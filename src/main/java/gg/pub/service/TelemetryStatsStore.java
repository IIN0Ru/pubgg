package gg.pub.service;

import gg.pub.domain.TelemetryStats;

import java.util.Optional;

/**
 * 한 경기·한 플레이어의 텔레메트리 분석 결과를 오래 보관하는 곳.
 * 끝난 경기는 바뀌지 않으니 한 번 분석하면 다시 계산할 필요가 없다. 서버를 다시 켜거나 재배포해도 남는다.
 * 구현이 둘이다: 운영(prod)은 DB, 그 밖(로컬 개발·테스트)은 저장 없이 메모리 캐시만 쓴다.
 *
 * 분석 방식이 바뀌어서 이전 결과를 못 믿게 되면 {@link TelemetryAnalyzer#VERSION} 을 올린다.
 * 버전이 다른 저장 결과는 없는 것으로 보고 다시 분석해서 덮어쓴다.
 */
public interface TelemetryStatsStore {

    /** 저장된 분석 결과. 없거나 버전이 다르면 빈 값 */
    Optional<TelemetryStats> find(String matchId, String accountId);

    /** 분석 결과를 저장한다 (이미 있으면 덮어쓴다) */
    void save(String matchId, String accountId, TelemetryStats stats);
}
