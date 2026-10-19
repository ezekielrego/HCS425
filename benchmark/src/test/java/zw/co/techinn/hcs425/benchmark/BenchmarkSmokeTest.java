package zw.co.techinn.hcs425.benchmark;

import org.junit.jupiter.api.Test;
import zw.co.techinn.hcs425.server.KeyValueServer;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkSmokeTest {
    @Test void tenClientsCompleteRealTcpWork() throws Exception {
        try (KeyValueServer server = new KeyValueServer("127.0.0.1", 0, 16, "benchmark-test")) {
            server.start();
            BenchmarkResult result = BenchmarkRunner.runOnce(server.port(), 10, 200);
            assertThat(result.operations()).isGreaterThan(0);
            assertThat(result.errors()).isZero();
            assertThat(result.throughputOpsSec()).isPositive();
        }
    }
}
