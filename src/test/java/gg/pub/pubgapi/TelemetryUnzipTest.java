package gg.pub.pubgapi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 텔레메트리 파일이 gzip 으로 오든, 그냥 오든 똑같이 읽히는지 확인한다.
 */
class TelemetryUnzipTest {

    private static final String JSON = "[{\"_T\":\"LogMatchStart\"}]";

    @Test
    void gzip이면_압축을_푼다() throws IOException {
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(zipped)) {
            gz.write(JSON.getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream in = PubgApiClient.unzipIfNeeded(new ByteArrayInputStream(zipped.toByteArray()))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(JSON);
        }
    }

    @Test
    void 압축이_아니면_그대로_돌려준다() throws IOException {
        byte[] plain = JSON.getBytes(StandardCharsets.UTF_8);

        try (InputStream in = PubgApiClient.unzipIfNeeded(new ByteArrayInputStream(plain))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(JSON);
        }
    }
}
