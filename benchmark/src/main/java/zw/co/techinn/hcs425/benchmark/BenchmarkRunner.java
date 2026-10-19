package zw.co.techinn.hcs425.benchmark;

import zw.co.techinn.hcs425.client.ClientException;
import zw.co.techinn.hcs425.client.KeyValueClient;
import zw.co.techinn.hcs425.server.KeyValueServer;
import zw.co.techinn.hcs425.shared.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed-total-workload TCP benchmark with coordinated starts and measured latency samples. */
public final class BenchmarkRunner {
    private static final int[] CLIENT_COUNTS = {1, 2, 5, 10, 20};
    private static final int TOTAL_OPERATIONS = 2_000;

    private BenchmarkRunner() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("hcs425.results", "results/csv/performance-results.csv"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (KeyValueServer server = new KeyValueServer("127.0.0.1", 0, 32, "benchmark")) {
            server.start();
            List<BenchmarkResult> results = new ArrayList<>();
            double baseline = 0;
            for (int clients : CLIENT_COUNTS) {
                BenchmarkResult result = runOnce(server.port(), clients, TOTAL_OPERATIONS);
                if (baseline == 0) baseline = result.elapsedSeconds();
                result = new BenchmarkResult(result.clients(), result.operations(), result.elapsedSeconds(), result.meanLatencyMs(),
                        result.medianLatencyMs(), result.p95LatencyMs(), result.throughputOpsSec(), baseline / result.elapsedSeconds(),
                        baseline / result.elapsedSeconds() / clients * 100d, result.errors());
                results.add(result);
                System.out.printf("%2d clients: %,d ops, %.3fs, %.1f ops/s, p95 %.3fms, errors %d%n",
                        clients, result.operations(), result.elapsedSeconds(), result.throughputOpsSec(), result.p95LatencyMs(), result.errors());
            }
            writeCsv(output, results);
        }
    }

    public static BenchmarkResult runOnce(int port, int clients, int operations) throws InterruptedException {
        int perClient = operations / clients;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(clients);
        List<Long> latencies = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        for (int clientId = 0; clientId < clients; clientId++) {
            final int id = clientId;
            pool.submit(() -> {
                try (KeyValueClient client = new KeyValueClient("127.0.0.1", port, 5_000)) {
                    client.connect();
                    for (int i = 0; i < 25; i++) client.ping();
                    ready.countDown();
                    start.await();
                    for (int i = 0; i < perClient; i++) {
                        String key = "bench-" + ((id * perClient + i) % 200);
                        long begin = System.nanoTime();
                        Response response = switch (i % 10) {
                            case 0, 1, 2 -> client.put(key, "value-" + id + "-" + i);
                            case 3 -> client.delete(key);
                            case 4 -> client.list();
                            default -> client.get(key);
                        };
                        latencies.add(System.nanoTime() - begin);
                        if (response.success()) completed.incrementAndGet(); else errors.incrementAndGet();
                    }
                } catch (ClientException | InterruptedException ex) {
                    errors.incrementAndGet();
                    Thread.currentThread().interrupt();
                } finally { done.countDown(); }
            });
        }
        ready.await(10, TimeUnit.SECONDS);
        long begin = System.nanoTime();
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        double seconds = (System.nanoTime() - begin) / 1_000_000_000d;
        pool.shutdownNow();
        List<Long> ordered = latencies.stream().sorted().toList();
        double mean = ordered.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000d;
        double median = percentile(ordered, 0.50) / 1_000_000d;
        double p95 = percentile(ordered, 0.95) / 1_000_000d;
        return new BenchmarkResult(clients, completed.get(), seconds, mean, median, p95, completed.get() / seconds, 0, 0, errors.get());
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = Math.min(values.size() - 1, Math.max(0, (int) Math.ceil(percentile * values.size()) - 1));
        return values.get(index);
    }

    private static void writeCsv(Path output, List<BenchmarkResult> results) throws IOException {
        StringBuilder csv = new StringBuilder("clients,operations,elapsed_seconds,mean_latency_ms,median_latency_ms,p95_latency_ms,throughput_ops_sec,speedup,efficiency_percent,errors\n");
        for (BenchmarkResult r : results) {
            csv.append(String.format(java.util.Locale.ROOT, "%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n",
                    r.clients(), r.operations(), r.elapsedSeconds(), r.meanLatencyMs(), r.medianLatencyMs(), r.p95LatencyMs(),
                    r.throughputOpsSec(), r.speedup(), r.efficiencyPercent(), r.errors()));
        }
        Files.writeString(output, csv, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
