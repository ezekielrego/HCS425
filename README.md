# HCS425 Distributed Key-Value System

A Java 21 Maven multi-module implementation of a multi-threaded TCP client/server key-value store with a JavaFX/AtlantaFX monitoring console, concurrency benchmark harness, and a bounded single-writer replication demonstration. The console provides a compact observability layout with metric cards, dense dark panels, live throughput/latency charts, health indicators, and operational activity tables.

## Modules

| Module | Purpose |
|---|---|
| `shared` | UTF-8 line-framed protocol, request/response models, LIST framing |
| `server` | `ServerSocket`, bounded `ExecutorService`, `ConcurrentHashMap`, metrics and logs |
| `client` | Timeout-aware synchronous client and JavaFX-safe asynchronous facade |
| `replication` | Leader-coordinated asynchronous update propagation to two replicas |
| `benchmark` | Coordinated real-TCP 1/2/5/10/20-client benchmark |
| `desktop` | JavaFX dashboard with AtlantaFX 2.0.1 theme and live operational controls |

## Build and run

Requirements: Java 21, Maven 3.8+, X11/Xvfb for the desktop application.

```bash
mvn clean verify
./scripts/start-server.sh
./scripts/start-desktop.sh
./scripts/run-benchmarks.sh
```

The server listens on `127.0.0.1:5050` by default. Override with `-Dhcs425.port=...`, `-Dhcs425.host=...`, or `-Dhcs425.workers=...`. The desktop starts an embedded local node when the port is free; set `-Dhcs425.embedded=false` to connect to an independently launched server.

The cluster demonstration uses:

```bash
./scripts/start-cluster.sh
# leader: 127.0.0.1:5050; replica B: 5051; replica C: 5052
```

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

## Evidence

Measured CSV output is written to `results/csv/performance-results.csv`, charts to `results/charts/`, screenshots to `results/screenshots/`, and the technical report to `docs/report/technical-report.md`. Requirement-by-requirement evidence is summarized in [requirements-coverage.md](docs/testing/requirements-coverage.md). The final NetBeans-openable package is placed in `submission/`.

## Screenshots

The following screenshots were captured from the running JavaFX application under Xvfb:

| Dashboard | Key-value operation | Performance results | Replication |
|---|---|---|---|
| [![Dashboard](results/screenshots/dashboard-live.png)](results/screenshots/dashboard-live.png) | [![PUT operation](results/screenshots/put-operation.png)](results/screenshots/put-operation.png) | [![Performance results](results/screenshots/performance-results.png)](results/screenshots/performance-results.png) | [![Replication](results/screenshots/replication.png)](results/screenshots/replication.png) |

Additional captures: [GET](results/screenshots/get-operation.png), [DELETE](results/screenshots/delete-operation.png), [LIST](results/screenshots/list-operation.png), [key-value page](results/screenshots/key-value.png), [client manager](results/screenshots/client-manager.png), [server logs](results/screenshots/server-logs.png), and [performance page](results/screenshots/performance-page.png).
