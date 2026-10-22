# HCS425 assignment coverage

This matrix maps the submitted implementation and measured evidence to the original HCS425 brief. Paths are relative to the repository root.

## Core implementation and testing — 40 marks

| Requirement | Implementation/evidence | Status |
|---|---|---|
| Java TCP client-server communication | `server/.../KeyValueServer.java` uses `ServerSocket` and persistent `Socket` connections; `client/.../KeyValueClient.java` is the real TCP client. `KeyValueClientTest` exercises the network path. | Complete |
| At least 10 simultaneous clients | Bounded `ThreadPoolExecutor` with independent client handlers. `BenchmarkSmokeTest` runs 10 real clients; `performance-results.csv` contains measured 1, 2, 5, 10, and 20-client runs. | Complete |
| Shared key-value store | `KeyValueStore` uses `ConcurrentHashMap` and supports PUT, GET, DELETE, and sorted LIST snapshots. `KeyValueStoreTest` and `KeyValueClientTest` cover the operations. | Complete |
| Synchronization and race prevention | Concurrent collection access protects the map; LIST copies and sorts a snapshot. `concurrentSameKeyWritesDoNotCorruptMap` starts 20 simultaneous writers against one key and verifies a consistent map state. | Complete |
| Fault tolerance and resource cleanup | Per-client exception handling, EOF/reset handling, 30-second read timeout, try-with-resources, bounded rejection handling, and server shutdown cleanup are implemented in `KeyValueServer`. `FaultToleranceHarness` aborts a socket, checks nine unaffected clients, reconnects a replacement, and records evidence. | Complete; runtime evidence recorded |
| Response time and throughput testing | `BenchmarkRunner` uses real TCP clients, warm-up pings, a coordinated latch, fixed total 2,000 operations, and records mean, median, p95, throughput, errors, speedup, and efficiency. | Complete |
| Required client counts | `results/csv/performance-results.csv` contains 1/2/5/10/20 clients with zero reported errors. An additional 10,000-operation capacity run covers 1/50/100 clients in `results/csv/stress-performance-results.csv`; charts are in `results/charts/` and `results/charts/stress/`. | Complete |
| Architecture and protocol explanation | `docs/architecture/architecture.svg`, `docs/architecture/replication.svg`, `docs/protocol/protocol.md`, and `docs/report/technical-report.md` describe the network, workers, synchronization, framing, race considerations, fault handling, and results. | Complete |

## Advanced replicated-server extension — 10 marks

| Requirement | Implementation/evidence | Status |
|---|---|---|
| Multiple server processes | `scripts/start-cluster.sh` launches independent A/B/C Java processes on ports 5050/5051/5052. | Complete |
| Leader-coordinated replication | `LeaderReplicator` assigns ordered sequence numbers and asynchronously sends encoded PUT/DELETE frames to both replicas. Replica application is idempotent by sequence number. | Complete |
| Replica failure tolerance | Replica communication failures are logged while the leader continues serving local client requests. `replication-evidence.txt` records successful propagation to both replicas and the documented eventual-consistency guarantee. | Implemented and documented |
| Consistency and concurrent updates | The design is single-writer and eventually consistent; client acknowledgement is local leader acknowledgement, not quorum durability. The limitation is explicitly stated in the report and UI. | Complete as scoped |
| Leader failure, recovery, and split-brain safety | Automatic leader election, quorum commit, network-partition resolution, and automatic missed-update reconciliation are not claimed. Failover is controlled/operator-driven to avoid unsafe split brain. | Explicit limitation; not full automatic failover |

## Verification commands

```bash
mvn clean verify
./scripts/run-benchmarks.sh
./scripts/run-fault-test.sh
./scripts/start-cluster.sh
```

The JavaFX console was launched and screenshots were captured under Xvfb. Apache NetBeans 31 project opening and the Maven workflow were verified with JDK 21. The root `pom.xml` is the portable entry point for any compatible NetBeans installation.
