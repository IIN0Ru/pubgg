package gg.pub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.pub.domain.TelemetryStats;
import gg.pub.pubgapi.PubgApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 저장된 분석 결과가 있으면 텔레메트리를 받지 않고, 저장소가 고장나도 검색은 된다. */
class TelemetryServiceTest {

    private static final String URL = "https://telemetry.example/m1.json";

    private PubgApiClient client;
    private TelemetryStatsStore store;
    private TelemetryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(PubgApiClient.class);
        store = mock(TelemetryStatsStore.class);
        service = new TelemetryService(client, new TelemetryAnalyzer(new ObjectMapper()), store);

        // 텔레메트리 파일은 빈 배열 [] 로 (이벤트가 없는 경기)
        when(client.readTelemetry(eq(URL), any())).thenAnswer(inv -> {
            Function<InputStream, Object> reader = inv.getArgument(1);
            return reader.apply(new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Test
    void 저장본이_있으면_텔레메트리를_받지_않는다() {
        TelemetryStats saved = new TelemetryStats(Map.of("Item_Weapon_HK416_C", 99L), 5);
        when(store.find("m1", "account.me")).thenReturn(Optional.of(saved));

        TelemetryStats result = service.analyze("m1", URL, "account.me");

        assertThat(result).isEqualTo(saved);
        verifyNoInteractions(client);
        verify(store, never()).save(any(), any(), any());
    }

    @Test
    void 저장본이_없으면_분석하고_저장한다() {
        when(store.find("m1", "account.me")).thenReturn(Optional.empty());

        TelemetryStats result = service.analyze("m1", URL, "account.me");

        assertThat(result.throwables()).isZero();
        verify(client).readTelemetry(eq(URL), any());
        verify(store).save("m1", "account.me", result);
    }

    @Test
    void 저장소를_못_읽거나_못_써도_분석_결과를_돌려준다() {
        when(store.find("m1", "account.me")).thenThrow(new IllegalStateException("DB 연결 끊김"));
        org.mockito.Mockito.doThrow(new IllegalStateException("DB 연결 끊김"))
                .when(store).save(any(), any(), any());

        TelemetryStats result = service.analyze("m1", URL, "account.me");

        assertThat(result).isNotNull();
        verify(client).readTelemetry(eq(URL), any());
    }
}
