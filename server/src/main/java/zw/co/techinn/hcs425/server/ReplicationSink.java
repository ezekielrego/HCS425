package zw.co.techinn.hcs425.server;

@FunctionalInterface
public interface ReplicationSink {
    void onLocalWrite(String key, String valueOrNull);
}
