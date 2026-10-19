package zw.co.techinn.hcs425.benchmark;

public record BenchmarkResult(int clients, int operations, double elapsedSeconds, double meanLatencyMs,
                              double medianLatencyMs, double p95LatencyMs, double throughputOpsSec,
                              double speedup, double efficiencyPercent, int errors) { }
