package zw.co.techinn.hcs425.replication;

import zw.co.techinn.hcs425.server.KeyValueServer;

import java.util.Arrays;
import java.util.List;

public final class ReplicationNodeMain {
    private ReplicationNodeMain() { }
    public static void main(String[] args) throws Exception {
        String name = System.getProperty("hcs425.node", "A");
        String role = System.getProperty("hcs425.role", "replica");
        String host = System.getProperty("hcs425.host", "127.0.0.1");
        int port = Integer.getInteger("hcs425.port", 5050);
        LeaderReplicator replicator = null;
        if ("leader".equalsIgnoreCase(role)) {
            String configured = System.getProperty("hcs425.replicas", "127.0.0.1:5051:B,127.0.0.1:5052:C");
            List<ReplicaEndpoint> endpoints = Arrays.stream(configured.split(","))
                    .map(String::trim).filter(s -> !s.isBlank()).map(ReplicationNodeMain::parseEndpoint).toList();
            replicator = new LeaderReplicator(endpoints);
        }
        LeaderReplicator finalReplicator = replicator;
        KeyValueServer server = new KeyValueServer(host, port, 32, name, replicator);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            if (finalReplicator != null) finalReplicator.close();
        }, "hcs425-cluster-shutdown-" + name));
        server.start();
        Thread.currentThread().join();
    }

    private static ReplicaEndpoint parseEndpoint(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) throw new IllegalArgumentException("Replica must be host:port:name: " + value);
        return new ReplicaEndpoint(parts[0], Integer.parseInt(parts[1]), parts[2]);
    }
}
