package zw.co.techinn.hcs425.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueStoreTest {
    @Test void basicOperationsWork() {
        KeyValueStore store = new KeyValueStore();
        store.put("student01", "Alice");
        assertThat(store.get("student01")).isEqualTo("Alice");
        assertThat(store.delete("student01")).isEqualTo("Alice");
        assertThat(store.get("student01")).isNull();
    }

    @Test void concurrentSameKeyWritesDoNotCorruptMap() throws Exception {
        KeyValueStore store = new KeyValueStore();
        int writers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        for (int i = 0; i < writers; i++) {
            int id = i;
            pool.submit(() -> {
                try { start.await(); store.put("shared", "value-" + id); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            });
        }
        start.countDown();
        done.await();
        pool.shutdownNow();
        assertThat(store.get("shared")).startsWith("value-");
        assertThat(store.snapshot()).hasSize(1);
    }
}
