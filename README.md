# HCS425 Distributed Key-Value System

A Java 21 Maven multi-module implementation of a multi-threaded TCP client/server key-value store with a JavaFX/AtlantaFX monitoring console, concurrency benchmark harness, and a bounded single-writer replication demonstration. The console provides a compact observability layout with metric cards, dense dark/light panels, live throughput/latency charts, health indicators, and operational activity tables.

This repository is the complete HCS425 submission project. It contains the source code, automated tests, runtime launchers, measured performance evidence, architecture diagrams, screenshots, technical report, and a NetBeans-compatible Maven reactor.

## Submission information

| Field | Details |
|---|---|
| Student | Ezekiel Rego |
| Registration number | M230913 |
| Course | HCS425 |

## What the system demonstrates

- Java TCP communication using `ServerSocket` and persistent UTF-8 `Socket` connections.
- Independent client handlers running in a bounded `ThreadPoolExecutor`.
- A shared `ConcurrentHashMap` store with `PUT`, `GET`, `DELETE`, and framed `LIST` operations.
- Connection, worker, request, latency, throughput, success, and failure metrics.
- Client timeouts, malformed-request handling, unexpected-disconnect cleanup, and overload rejection.
- A real TCP benchmark for 1, 2, 5, 10, and 20 concurrent clients.
- A separate 50/100-client capacity stress run using a larger bounded worker pool.
- A JavaFX console that performs real operations without blocking the JavaFX Application Thread.
- A three-process replication demonstration with leader A and replicas B and C.

## Modules

| Module | Purpose |
|---|---|
| `shared` | UTF-8 line-framed protocol, request/response models, LIST framing |
| `server` | `ServerSocket`, bounded `ExecutorService`, `ConcurrentHashMap`, metrics and logs |
| `client` | Timeout-aware synchronous client and JavaFX-safe asynchronous facade |
| `replication` | Leader-coordinated asynchronous update propagation to two replicas |
| `benchmark` | Coordinated real-TCP required benchmark plus configurable stress counts |
| `desktop` | JavaFX dashboard with AtlantaFX 2.0.1 theme and live operational controls |

The root project is a Maven parent project. Build from the repository root so Maven builds modules in dependency order: `shared` → `server` → `client` → `replication` → `benchmark` → `desktop`.

## Requirements

For the supplied VPS setup, use Java JDK 21, Maven 3.8 or newer, Git, and a graphical display for the desktop console. JavaFX dependencies are resolved by Maven. On a headless Linux VPS, Xvfb is sufficient for automated launch and screenshots.

Check the environment before opening the project:

```bash
java -version
javac -version
mvn -version
```

## Build and run from Apache NetBeans

The project is a standard Maven reactor and opens in any compatible Apache NetBeans installation.

1. Start Apache NetBeans.
2. Select **File → Open Project**.
3. Choose the repository root, the directory containing the root `pom.xml`.
4. Allow NetBeans to resolve Maven dependencies and index the six modules.
5. Right-click the root project and choose **Build** or **Test**. The custom Maven actions in `nbactions.xml` provide `verify`, `test`, and `package` actions.

The safest way to launch long-running applications from NetBeans is the built-in terminal, because the scripts configure the correct multi-module classpaths and process lifecycle. Open **Window → IDE Tools → Terminal**, change to the repository root, and use one terminal tab per process.

Run the complete build and tests:

```bash
mvn clean verify
```

### NetBeans evidence

The root Maven project should appear in NetBeans and the reactor build should complete successfully. These are the first submission screenshots; the remote development environment is not required.

| Project opened | Maven build successful |
|---|---|
| [![Project opened in NetBeans](results/screenshots/01-project-open.png)](results/screenshots/01-project-open.png) | [![Maven build successful](results/screenshots/02-build-success.png)](results/screenshots/02-build-success.png) |

Run the desktop with its embedded local server:

```bash
./scripts/start-desktop.sh
```

On a normal desktop this uses the existing display. On a headless VPS, the script starts Xvfb automatically when `DISPLAY` is unset. To launch explicitly on a virtual display:

```bash
Xvfb :99 -screen 0 1280x800x24 -ac +extension GLX &
export DISPLAY=:99
./scripts/start-desktop.sh
```

The application opens in light mode by default. The dashboard below shows the live TCP status and monitoring layout after startup:

[![Light dashboard](results/screenshots/dashboard-light-latest.png)](results/screenshots/dashboard-light-latest.png)

The desktop starts an embedded server on `127.0.0.1:5050` by default. To run the server independently, use two terminal tabs:

```bash
# Terminal 1
./scripts/start-server.sh

# Terminal 2
HCS425_EMBEDDED=false ./scripts/start-desktop.sh
```

Do not run the root command `mvn javafx:run`; the JavaFX main class belongs to the `desktop` module. If launching directly with Maven, use:

```bash
mvn -pl desktop javafx:run -Dhcs425.embedded=true
```

Stop a running server, desktop, or cluster with `Ctrl+C`. Do not start the standalone server on port 5050 at the same time as the desktop's embedded server.

## Demonstration evidence

The following workflow demonstrates the implemented requirements. The screenshots appear beside the operation they document.

### 1. Demonstrate the key-value protocol

In **Key-Value Store**, execute `PUT`, `GET`, `LIST`, and `DELETE`. A key such as `student01` and a value containing spaces such as `Alice in Harare` demonstrate the protocol's value parsing. The UI displays the server response and measured response time; LIST displays the actual stored row.

| PUT | GET | LIST | DELETE |
|---|---|---|---|
| [![PUT](results/screenshots/put-operation-light.png)](results/screenshots/put-operation-light.png) | [![GET](results/screenshots/get-operation-light.png)](results/screenshots/get-operation-light.png) | [![LIST](results/screenshots/list-operation-light.png)](results/screenshots/list-operation-light.png) | [![DELETE](results/screenshots/delete-operation-light.png)](results/screenshots/delete-operation-light.png) |

The same interaction is also available in the original dark theme: [PUT](results/screenshots/put-operation.png), [GET](results/screenshots/get-operation.png), [LIST](results/screenshots/list-operation.png), and [DELETE](results/screenshots/delete-operation.png).

### 2. Demonstrate simultaneous clients

The **Client Manager** supports 1–100 simulated clients. The assignment threshold is demonstrated with 10 or 20 clients; the capture below shows 50 real TCP connections held open while the server table refreshed.

[![Fifty active TCP clients](results/screenshots/client-manager-50-light.png)](results/screenshots/client-manager-50-light.png)

### 3. Demonstrate performance testing

The **Performance Testing** page provides the required comparison of 1, 2, 5, 10, and 20 clients with the same total workload. The additional 50/100 controls are capacity tests and are reported separately below. For a 50/100-client UI run, use `HCS425_WORKERS=128 ./scripts/start-desktop.sh`; the standard demonstration uses the default 32-worker pool:

[![Performance testing controls with 100 clients](results/screenshots/performance-100-light.png)](results/screenshots/performance-100-light.png)

### 4. Demonstrate server logs and fault handling

The **Server Logs** page shows startup, accepted connections, requests, and disconnect cleanup. The automated fault harness aborts one client, confirms nine unaffected clients, reconnects a replacement, and verifies that the server remains running.

[![Server logs](results/screenshots/server-logs-light.png)](results/screenshots/server-logs-light.png)

Run the fault test from the repository root:

```bash
./scripts/run-fault-test.sh
```

Expected evidence is recorded in [`results/csv/fault-tolerance.txt`](results/csv/fault-tolerance.txt).

### 5. Demonstrate replication

The **Replication** page reports node roles and health after the three-process cluster is started. The implementation documents asynchronous eventual consistency and controlled failover rather than claiming consensus.

The server listens on `127.0.0.1:5050` by default. Override with `HCS425_HOST`, `HCS425_PORT`, and `HCS425_WORKERS` for `start-server.sh`, or with `-Dhcs425.host=...`, `-Dhcs425.port=...`, and `-Dhcs425.workers=...` when launching Java directly.

The cluster demonstration uses:

```bash
./scripts/start-cluster.sh
# leader: 127.0.0.1:5050; replica B: 5051; replica C: 5052
```

Leave the cluster terminal running, then use another terminal to verify propagation:

```bash
printf 'PUT demo replicated-value\n' | nc -w 2 127.0.0.1 5050
sleep 1
printf 'GET demo\n' | nc -w 2 127.0.0.1 5051
printf 'GET demo\n' | nc -w 2 127.0.0.1 5052
```

Both replicas should return `OK VALUE replicated-value`. Cluster logs are written to `logs/node-A.log`, `logs/node-B.log`, and `logs/node-C.log`.

[![Replication monitoring page](results/screenshots/replication.png)](results/screenshots/replication.png)

## Testing and performance workflow

Run all automated protocol, store, real TCP, concurrency, replication, benchmark-smoke, and desktop-resource tests:

```bash
mvn clean verify
```

Run the full measured fixed-total-workload benchmark:

```bash
./scripts/run-benchmarks.sh
```

This executes 2,000 total operations at 1, 2, 5, 10, and 20 clients using real TCP connections. It writes `results/csv/performance-results.csv` and the SVG charts in `results/charts/`. The CSV contains mean latency, median latency, p95 latency, throughput, speedup, efficiency, and errors.

Recorded measured run on the four-core test host:

| Clients | Operations | Throughput ops/s | Mean ms | p95 ms | Speedup | Efficiency | Errors |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2,000 | 1,874.39 | 0.511 | 1.845 | 1.000x | 100.00% | 0 |
| 2 | 2,000 | 6,120.21 | 0.321 | 0.725 | 3.265x | 163.26% | 0 |
| 5 | 2,000 | 7,119.30 | 0.594 | 2.896 | 3.798x | 75.96% | 0 |
| 10 | 2,000 | 8,256.64 | 1.136 | 3.864 | 4.405x | 44.05% | 0 |
| 20 | 2,000 | 6,842.28 | 2.312 | 6.688 | 3.650x | 18.25% | 0 |

Measured result charts: [throughput](results/charts/throughput.svg), [speedup](results/charts/speedup.svg), and [efficiency](results/charts/efficiency.svg).

![Primary measured throughput](results/charts/throughput.svg)

### Additional 50 versus 100 client capacity test

This separate run keeps a fixed 10,000-operation workload and uses 128 bounded workers so the comparison measures client concurrency rather than the normal 32-worker overload limit. It is additional evidence, not a replacement for the required 1/2/5/10/20 run.

```bash
./scripts/run-stress-benchmarks.sh
```

| Clients | Operations | Elapsed (s) | Mean (ms) | p95 (ms) | Throughput | Speedup | Efficiency | Errors |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 10,000 | 2.422105 | 0.236 | 0.737 | 4,128.64 | 1.000x | 100.00% | 0 |
| 50 | 10,000 | 1.216913 | 5.606 | 30.370 | 8,217.51 | 1.990x | 3.98% | 0 |
| 100 | 10,000 | 1.195404 | 10.864 | 52.917 | 8,365.37 | 2.026x | 2.03% | 0 |

The extra run is stored in [`results/csv/stress-performance-results.csv`](results/csv/stress-performance-results.csv), with charts under [`results/charts/stress/`](results/charts/stress/). It shows that throughput improves only slightly from 50 to 100 clients while p95 latency rises, consistent with saturation on the four-core test host.

![50/100-client stress throughput](results/charts/stress/throughput.svg)

Run the unexpected-client-failure test:

```bash
./scripts/run-fault-test.sh
```

The harness aborts one socket while nine other clients continue, then connects a replacement client. Evidence is written to `results/csv/fault-tolerance.txt`.

For a focused module test, use Maven's module selector:

```bash
mvn -pl shared test
mvn -pl server -am test
mvn -pl client -am test
mvn -pl replication -am test
mvn -pl benchmark -am test
mvn -pl desktop -am test
```

The complete requirement-to-evidence mapping, including the advanced replication limitation, is in [requirements-coverage.md](docs/testing/requirements-coverage.md).

## Protocol

Commands are one UTF-8 line per request:

```text
PUT key value with spaces
GET key
DELETE key
LIST
PING
STATS
```

Keys cannot contain whitespace. PUT values may contain spaces but cannot contain a newline. LIST responses are framed as `OK LIST count`, exactly `count` base64-url encoded `ITEM` lines, and `END LIST`. See [protocol.md](docs/protocol/protocol.md).

Example exchange:

```text
PUT student01 Alice in Harare
OK STORED
GET student01
OK VALUE Alice in Harare
DELETE student01
OK DELETED
LIST
OK LIST 0
END LIST
```

A client may keep one connection open and send multiple commands. Malformed requests return an error response; idle clients are closed after the server read timeout.

## Architecture and synchronization

```text
JavaFX Client / Benchmark Clients
            │ persistent UTF-8 TCP
            ▼
       ServerSocket acceptor
            │
            ▼
 bounded ExecutorService workers
            │
            ▼
     Protocol parser/executor
            │
            ▼
 ConcurrentHashMap key-value store
```

The acceptor is separate from client workers. Each accepted connection is registered, assigned to the bounded pool, processed independently, and removed in a `finally` block. A slow, malformed, timed-out, or disconnected client does not terminate the server or stop unrelated workers. Basic map operations are thread-safe through `ConcurrentHashMap`; `LIST` creates a sorted snapshot instead of exposing live map state.

The advanced topology uses a single-writer leader:

```text
                 ┌──────────────┐
                 │ Server A     │
                 │ Leader       │
                 └──────┬───────┘
                        │ ordered async updates
                 ┌──────┴───────┐
                 ▼              ▼
          Server B         Server C
          Replica           Replica
```

Writes are acknowledged after the leader updates its local in-memory store and are then propagated with sequence numbers. This is eventual asynchronous replication, not quorum durability. Automatic leader election and network-partition resolution are intentionally not claimed.

## Evidence and submission files

Measured CSV output is written to `results/csv/performance-results.csv`, charts to `results/charts/`, screenshots to `results/screenshots/`, and the technical report to `docs/report/technical-report.md`. Requirement-by-requirement evidence is summarized in [requirements-coverage.md](docs/testing/requirements-coverage.md). The final NetBeans-openable package is placed in `submission/`.

Important submission files:

- [Technical report](docs/report/technical-report.md)
- [Protocol specification](docs/protocol/protocol.md)
- [Core architecture diagram](docs/architecture/architecture.svg)
- [Replication architecture diagram](docs/architecture/replication.svg)
- [Test evidence](docs/testing/test-evidence.md)
- [Test environment record](docs/testing/vps-environment.md)
- [Open-source notices](LICENSES.md)
- [Submission ZIP](submission/hcs425-distributed-system.zip)

The ZIP excludes Maven `target/` directories, temporary logs, caches, and unrelated VPS files. Recreate it after changing evidence with:

```bash
zip -r -q -FS submission/hcs425-distributed-system.zip . \
  -x './.git/*' './*/target/*' './submission/*.zip' './logs/*' './*.log'
```

## Limitations

- The key-value store is in memory and is not durable across process restarts.
- Replication is single-writer asynchronous eventual consistency.
- Replica unavailability is logged while the leader continues local service.
- Automatic leader election, quorum commit, consensus, network-partition resolution, and automatic missed-update reconciliation are not implemented or claimed.
- Apache NetBeans 31 project opening was verified with JDK 21; the Maven reactor build, dependency resolution, tests, JavaFX launch, resources, and NetBeans Maven metadata were also verified.

## Requirement-to-evidence map

| Assignment requirement | README evidence |
|---|---|
| TCP client-server communication and server status | [Light dashboard](results/screenshots/dashboard-light-latest.png), [key-value page](results/screenshots/key-value.png), [protocol specification](docs/protocol/protocol.md) |
| PUT, GET, DELETE and LIST operations | [PUT](results/screenshots/put-operation-light.png), [GET](results/screenshots/get-operation-light.png), [DELETE](results/screenshots/delete-operation-light.png), [LIST](results/screenshots/list-operation-light.png) |
| At least 10 simultaneous clients and 20-client test | [50-client manager capture](results/screenshots/client-manager-50-light.png), [performance controls](results/screenshots/performance-100-light.png), [measured CSV](results/csv/performance-results.csv) |
| Threading, synchronization and race-condition protection | [Core architecture diagram](docs/architecture/architecture.svg), [requirements coverage](docs/testing/requirements-coverage.md), [test evidence](docs/testing/test-evidence.md) |
| Response time, throughput, speedup and efficiency | [Performance controls](results/screenshots/performance-100-light.png), [primary performance CSV](results/csv/performance-results.csv), [50/100 stress CSV](results/csv/stress-performance-results.csv), [technical report](docs/report/technical-report.md) |
| Malformed requests and failed-client cleanup | [Light server logs](results/screenshots/server-logs-light.png), [fault-tolerance evidence](results/csv/fault-tolerance.txt) |
| Replicated servers and synchronization behavior | [Replication monitor](results/screenshots/replication.png), [replication diagram](docs/architecture/replication.svg), [replication evidence](results/csv/replication-evidence.txt) |
| Architecture and implementation explanation | [Architecture diagrams](docs/architecture/), [technical report](docs/report/technical-report.md) |
