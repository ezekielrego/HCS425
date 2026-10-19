package zw.co.techinn.hcs425.client;

import org.junit.jupiter.api.Test;
import zw.co.techinn.hcs425.server.KeyValueServer;
import zw.co.techinn.hcs425.shared.Response;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueClientTest {
    @Test void clientUsesRealTcpServer() throws Exception {
        try (KeyValueServer server = new KeyValueServer("127.0.0.1", 0, 8, "test")) {
            server.start();
            try (KeyValueClient client = new KeyValueClient("127.0.0.1", server.port())) {
                assertThat(client.put("hello", "world with spaces").message()).isEqualTo("STORED");
                assertThat(client.get("hello").value()).isEqualTo("world with spaces");
                assertThat(client.listEntries()).hasSize(1);
                assertThat(client.delete("hello").message()).isEqualTo("DELETED");
            }
        }
    }
}
