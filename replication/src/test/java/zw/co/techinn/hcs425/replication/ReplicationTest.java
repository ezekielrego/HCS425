package zw.co.techinn.hcs425.replication;

import org.junit.jupiter.api.Test;
import zw.co.techinn.hcs425.client.KeyValueClient;
import zw.co.techinn.hcs425.server.KeyValueServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplicationTest {
    @Test void leaderPropagatesOrderedWritesToReplica() throws Exception {
        try (KeyValueServer replica = new KeyValueServer("127.0.0.1", 0, 8, "B")) {
            replica.start();
            try (LeaderReplicator replicator = new LeaderReplicator(List.of(new ReplicaEndpoint("127.0.0.1", replica.port(), "B")));
                 KeyValueServer leader = new KeyValueServer("127.0.0.1", 0, 8, "A", replicator)) {
                leader.start();
                try (KeyValueClient client = new KeyValueClient("127.0.0.1", leader.port())) {
                    client.put("replicated", "yes");
                }
                long deadline = System.currentTimeMillis() + 3_000;
                String value = null;
                try (KeyValueClient replicaClient = new KeyValueClient("127.0.0.1", replica.port())) {
                    while (System.currentTimeMillis() < deadline && value == null) {
                        var response = replicaClient.get("replicated");
                        value = response.value();
                        if (value == null) Thread.sleep(20);
                    }
                }
                assertThat(value).isEqualTo("yes");
            }
        }
    }
}
