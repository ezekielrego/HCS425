# HCS425 Multi-Threaded Distributed Key-Value System

**Student:** Ezekiel Rego  
**Registration number:** M230913

## 1. Introduction and objectives

This submission implements a working TCP client-server key-value system and an operational JavaFX monitoring console. It demonstrates persistent connections, bounded multi-threading, safe shared state, failure isolation, measured concurrency, and a deliberately scoped replicated-server extension.

## 2. Requirements and technologies

Java 21, Maven 3.8.7, JavaFX 21.0.7, AtlantaFX 2.0.1, JUnit 5.11.4, standard Java TCP sockets, `ExecutorService`, `ConcurrentHashMap`, Xvfb, and Graphviz are used. The JavaFX console uses a compact observability layout implemented with AtlantaFX styling and JavaFX charts. The project is a Maven reactor and can be opened from the root `pom.xml` in NetBeans.

## 3. Architecture

The client opens a persistent UTF-8 TCP connection to `ServerSocket`. Each accepted socket is assigned to a bounded worker pool. The worker reads commands sequentially for that client, executes against the shared map, writes a framed response, and continues until disconnect or timeout. See `docs/architecture/architecture.svg` and `replication.svg`.

## 4. Protocol and synchronization

The protocol is line framed; LIST uses count/item/end framing and base64-url fields. Keys are whitespace-free and values may contain spaces. `ConcurrentHashMap` protects individual map operations. LIST creates a sorted snapshot. Replication uses ordered sequence numbers and idempotent replica application.

## 5. Fault tolerance

Malformed requests are rejected per connection. Socket timeouts, EOF, reset connections, rejected workers, and unexpected disconnects are logged and cleaned up. The server acceptor and other workers continue independently. A 10-client abort scenario is recorded in `results/csv/fault-tolerance.txt`.

## 6. Concurrency and performance testing

The benchmark establishes real TCP connections, warms each connection, coordinates the start with a latch, and measures a fixed total of 2,000 operations at 1, 2, 5, 10, and 20 clients. Each run uses the same deterministic mixed workload and reports mean, median, p95, throughput, speedup, efficiency, and errors. A separate fixed 10,000-operation capacity run measures 1, 50, and 100 clients with 128 bounded workers. The authoritative values are in `results/csv/performance-results.csv` and `results/csv/stress-performance-results.csv`; charts are generated directly from those files.

### Measured environment

Ubuntu 24.04.4 LTS, x86_64, 4 logical CPUs, 7.8 GiB RAM, Java 21.0.12.1, Maven 3.8.7. The generated CSV, charts, and fault-tolerance evidence are stored under `results/`.

### Primary measured run

| Clients | Operations | Elapsed (s) | Mean (ms) | Median (ms) | p95 (ms) | Throughput | Speedup | Efficiency | Errors |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2,000 | 1.067013 | 0.511305 | 0.196216 | 1.845037 | 1,874.39 | 1.000x | 100.00% | 0 |
| 2 | 2,000 | 0.326786 | 0.321268 | 0.214760 | 0.725121 | 6,120.21 | 3.265x | 163.26% | 0 |
| 5 | 2,000 | 0.280926 | 0.594146 | 0.212916 | 2.895954 | 7,119.30 | 3.798x | 75.96% | 0 |
| 10 | 2,000 | 0.242229 | 1.136053 | 0.473612 | 3.863786 | 8,256.64 | 4.405x | 44.05% | 0 |
| 20 | 2,000 | 0.292300 | 2.312393 | 1.178455 | 6.688147 | 6,842.28 | 3.650x | 18.25% | 0 |

The greater-than-100% two-client efficiency is a measured result of the very small in-memory workload and one-client scheduling/connection effects; it is not interpreted as physically superlinear CPU speedup. At 20 clients, queueing and contention reduce efficiency and increase p95 latency.

### Additional capacity run

| Clients | Operations | Elapsed (s) | Mean (ms) | Median (ms) | p95 (ms) | Throughput | Speedup | Efficiency | Errors |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 10,000 | 2.422105 | 0.235542 | 0.139379 | 0.737033 | 4,128.64 | 1.000x | 100.00% | 0 |
| 50 | 10,000 | 1.216913 | 5.606318 | 1.012405 | 30.369606 | 8,217.51 | 1.990x | 3.98% | 0 |
| 100 | 10,000 | 1.195404 | 10.864482 | 0.528275 | 52.917423 | 8,365.37 | 2.026x | 2.03% | 0 |

The 50-to-100 comparison shows only a small throughput increase and a substantial p95-latency increase, indicating saturation on the four-logical-CPU VPS rather than linear scaling.

## 7. Replication design

Server A is the single writer. A successful local PUT/DELETE is assigned a monotonically increasing sequence and asynchronously sent to B and C. Replica frames are idempotent by sequence number. Client acknowledgement is local leader acknowledgement, so the guarantee is eventual propagation, not quorum durability. Controlled operator failover is documented; automatic leader election is not claimed, preventing an unsafe split-brain interpretation.

## 8. Limitations

The store is in memory and not durable. Replication does not implement consensus, quorum commit, automatic election, or network-partition resolution. Replica resynchronization after an extended outage is a documented follow-up rather than an unverified claim. These boundaries are reflected in the UI and report.

## 9. Conclusion and evidence

`mvn clean verify`, the benchmark script, the fault harness, the three-process cluster script, diagrams, screenshots, and CSV evidence are the reproducible submission artifacts. The runtime evidence directory contains 1280×800 PNG captures from the running JavaFX process, including live dashboard metrics and performance-table output. The requirement mapping is in `docs/testing/requirements-coverage.md`. Apache NetBeans 31 was also used on the author's VPS display to verify project opening and the Maven workflow; the lecturer can independently open the root `pom.xml` in their own NetBeans installation.
