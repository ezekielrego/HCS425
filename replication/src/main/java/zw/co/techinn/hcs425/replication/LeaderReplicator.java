package zw.co.techinn.hcs425.replication;

import zw.co.techinn.hcs425.client.ClientException;
import zw.co.techinn.hcs425.client.KeyValueClient;
import zw.co.techinn.hcs425.server.ReplicationSink;
import zw.co.techinn.hcs425.shared.Protocol;
import zw.co.techinn.hcs425.shared.Response;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** Single-writer, asynchronous replication sink. Client acknowledgement is local-store acknowledgement. */
public final class LeaderReplicator implements ReplicationSink, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(LeaderReplicator.class.getName());
    private final List<ReplicaEndpoint> replicas;
    private final AtomicLong sequence = new AtomicLong();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hcs425-replication"); t.setDaemon(true); return t;
    });

    public LeaderReplicator(List<ReplicaEndpoint> replicas) { this.replicas = List.copyOf(replicas); }

    @Override public void onLocalWrite(String key, String valueOrNull) {
        long seq = sequence.incrementAndGet();
        executor.submit(() -> {
            String operation = valueOrNull == null ? "DELETE" : "PUT";
            String frame = valueOrNull == null
                    ? String.format("REPL %d DELETE %s", seq, Protocol.encode(key))
                    : String.format("REPL %d PUT %s %s", seq, Protocol.encode(key), Protocol.encode(valueOrNull));
            for (ReplicaEndpoint replica : replicas) {
                try (KeyValueClient client = new KeyValueClient(replica.host(), replica.port(), 1_000)) {
                    Response response = client.send(frame);
                    LOG.info(() -> "replication seq=" + seq + " " + operation + " -> " + replica.name() + " " + response.message());
                } catch (ClientException ex) {
                    LOG.warning(() -> "replication seq=" + seq + " -> " + replica.name() + " unavailable: " + ex.getMessage());
                }
            }
        });
    }

    public long lastIssuedSequence() { return sequence.get(); }
    @Override public void close() { executor.shutdownNow(); }
}
