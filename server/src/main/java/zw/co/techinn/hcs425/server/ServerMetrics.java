package zw.co.techinn.hcs425.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class ServerMetrics {
    private final long startedNanos = System.nanoTime();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final LongAdder acceptedConnections = new LongAdder();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();

    void connectionOpened() { activeConnections.incrementAndGet(); acceptedConnections.increment(); }
    void connectionClosed() { activeConnections.decrementAndGet(); }
    void workerStarted() { activeWorkers.incrementAndGet(); }
    void workerFinished() { activeWorkers.decrementAndGet(); }
    void requestFinished(boolean success, long latencyNanos) {
        totalRequests.increment();
        if (success) successfulRequests.increment(); else failedRequests.increment();
        totalLatencyNanos.add(latencyNanos);
    }

    public Snapshot snapshot() {
        long requests = totalRequests.sum();
        double avgMs = requests == 0 ? 0 : totalLatencyNanos.sum() / 1_000_000d / requests;
        double uptimeSeconds = Math.max(0.001, (System.nanoTime() - startedNanos) / 1_000_000_000d);
        return new Snapshot(activeConnections.get(), activeWorkers.get(), acceptedConnections.sum(), requests,
                successfulRequests.sum(), failedRequests.sum(), avgMs, requests / uptimeSeconds);
    }

    public record Snapshot(int activeConnections, int activeWorkers, long acceptedConnections,
                           long totalRequests, long successfulRequests, long failedRequests,
                           double averageLatencyMs, double throughputOpsSec) { }
}
