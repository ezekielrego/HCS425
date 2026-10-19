package zw.co.techinn.hcs425.server;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientConnectionInfo {
    private final String clientId;
    private final String remoteAddress;
    private final Instant connectedAt;
    private final AtomicLong requestCount = new AtomicLong();
    private volatile Instant lastActivity;

    public ClientConnectionInfo(String clientId, String remoteAddress) {
        this.clientId = clientId;
        this.remoteAddress = remoteAddress;
        this.connectedAt = Instant.now();
        this.lastActivity = connectedAt;
    }

    public String clientId() { return clientId; }
    public String remoteAddress() { return remoteAddress; }
    public Instant connectedAt() { return connectedAt; }
    public long requestCount() { return requestCount.get(); }
    public Instant lastActivity() { return lastActivity; }
    void requestReceived() { requestCount.incrementAndGet(); lastActivity = Instant.now(); }
}
