package gg.pub.service;

import gg.pub.domain.MatchRecord;
import gg.pub.domain.Platform;
import gg.pub.domain.replay.MapInfo;
import gg.pub.domain.replay.Replay;
import gg.pub.pubgapi.PubgApiClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 2D 리플레이 데이터를 만든다.
 *
 *  1. 경기 기록(MatchRecordService, 이미 캐시돼 있으면 API 를 다시 부르지 않음)으로 경쟁전인지·지도·텔레메트리 주소 확인
 *  2. 텔레메트리를 받아 ReplayBuilder 로 줄인다
 *  3. 결과를 (경기, 플레이어) 별로 저장해 둔다. 끝난 경기는 바뀌지 않아서
 *
 * 주의: 한 경기 리플레이가 수백 KB 라 서버 메모리에 많이 쌓일 수 있다. 배포할 때 저장 개수 제한 검토.
 */
@Service
public class ReplayService {

    private final MatchRecordService matchRecordService;
    private final PubgApiClient client;
    private final ReplayBuilder builder;

    public ReplayService(MatchRecordService matchRecordService, PubgApiClient client, ReplayBuilder builder) {
        this.matchRecordService = matchRecordService;
        this.client = client;
        this.builder = builder;
    }

    /** 화면 위쪽 제목(맵·등수·날짜)용 경기 기록 */
    public MatchRecord record(Platform platform, String matchId, String accountId) {
        MatchRecord record = matchRecordService.get(platform, matchId, accountId)
                .orElseThrow(() -> new ReplayUnavailableException("이 경기에서 플레이어 기록을 찾지 못했어요."));
        if (!record.isCompetitive()) {
            throw new ReplayUnavailableException("리플레이는 경쟁전 경기만 볼 수 있어요.");
        }
        return record;
    }

    @Cacheable(cacheNames = "replays", key = "#matchId + ':' + #accountId")
    public Replay replay(Platform platform, String matchId, String accountId) {
        MatchRecord record = record(platform, matchId, accountId);
        MapInfo map = MapInfo.of(record.mapName())
                .orElseThrow(() -> new ReplayUnavailableException(record.map() + " 맵은 아직 리플레이를 지원하지 않아요."));
        if (record.telemetryUrl() == null) {
            throw new ReplayUnavailableException("이 경기는 상세 기록이 없어서 리플레이를 만들 수 없어요.");
        }
        return client.readTelemetry(record.telemetryUrl(), body -> builder.build(body, matchId, map, accountId));
    }
}
