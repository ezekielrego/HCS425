# HCS425 Multi-Threaded Distributed Key-Value System

## 1. Introduction and objectives

This submission implements a working TCP client-server key-value system and an operational JavaFX monitoring console. It demonstrates persistent connections, bounded multi-threading, safe shared state, failure isolation, measured concurrency, and a deliberately scoped replicated-server extension.

## 2. Requirements and technologies

Java 21, Maven 3.8.7, JavaFX 21.0.7, AtlantaFX 2.0.1, JUnit 5.11.4, standard Java TCP sockets, `ExecutorService`, `ConcurrentHashMap`, Xvfb, and Graphviz are used. The project is a Maven reactor and can be opened from the root `pom.xml` in NetBeans.

## 3. Architecture

The client opens a persistent UTF-8 TCP connection to `ServerSocket`. Each accepted socket is assigned to a bounded worker pool. The worker reads commands sequentially for that client, executes against the shared map, writes a framed response, and continues until disconnect or timeout. See `docs/architecture/architecture.svg` and `replication.svg`.

## 4. Protocol and synchronization

The protocol is line framed; LIST uses count/item/end framing and base64-url fields. Keys are whitespace-free and values may contain spaces. `ConcurrentHashMap` protects individual map operations. LIST creates a sorted snapshot. Replication uses ordered sequence numbers and idempotent replica application.

## 5. Fault tolerance

Malformed requests are rejected per connection. Socket timeouts, EOF, reset connections, rejected workers, and unexpected disconnects are logged and cleaned up. The server acceptor and other workers continue independently. A 10-client abort scenario is recorded in `results/csv/fault-tolerance.txt`.

## 6. Concurrency and performance testing

The benchmark establishes real TCP connections, warms each connection, coordinates the start with a latch, and measures a fixed total of 2,000 operations at 1, 2, 5, 10, and 20 clients. Each run uses the same deterministic mixed workload and reports mean, median, p95, throughput, speedup, efficiency, and errors. The authoritative values are in `results/csv/performance-results.csv`; charts are generated directly from that file.

### Measured environment

Ubuntu 24.04.4 LTS, x86_64, 4 logical CPUs, 7.8 GiB RAM, Java 21.0.12.1, Maven 3.8.7. Record the generated CSV and command output alongside this report after the benchmark run.

### Primary measured run

| Clients | Operations | Elapsed (s) | Mean (ms) | Median (ms) | p95 (ms) | Throughput | Speedup | Efficiency | Errors |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2,000 | 0.523672 | 0.251594 | 0.145130 | 0.727896 | 3,819.18 | 1.000x | 100.00% | 0 |
| 2 | 2,000 | 0.294243 | 0.267854 | 0.147965 | 0.670249 | 6,797.11 | 1.780x | 88.99% | 0 |
| 5 | 2,000 | 0.218399 | 0.521927 | 0.365842 | 1.076726 | 9,157.54 | 2.398x | 47.96% | 0 |
| 10 | 2,000 | 0.217970 | 0.919917 | 0.581965 | 2.629329 | 9,175.56 | 2.402x | 24.02% | 0 |
| 20 | 2,000 | 0.171434 | 1.455521 | 0.610808 | 6.815658 | 11,666.27 | 3.055x | 15.27% | 0 |

The greater-than-100% two-client efficiency is a measured result of the very small in-memory workload and one-client scheduling/connection effects; it is not interpreted as physically superlinear CPU speedup. At 20 clients, queueing and contention reduce efficiency and increase p95 latency.

## 7. Replication design

Server A is the single writer. A successful local PUT/DELETE is assigned a monotonically increasing sequence and asynchronously sent to B and C. Replica frames are idempotent by sequence number. Client acknowledgement is local leader acknowledgement, so the guarantee is eventual propagation, not quorum durability. Controlled operator failover is documented; automatic leader election is not claimed, preventing an unsafe split-brain interpretation.

## 8. Limitations

The store is in memory and not durable. Replication does not implement consensus, quorum commit, automatic election, or network-partition resolution. Replica resynchronization after an extended outage is a documented follow-up rather than an unverified claim. These boundaries are reflected in the UI and report.

## 9. Conclusion and evidence

`mvn clean verify`, the benchmark script, the Xvfb desktop launch, cluster script, diagrams, screenshots, and CSV evidence are the reproducible submission artifacts. The runtime evidence directory contains 1280×800 PNG captures from the running JavaFX process, including live dashboard metrics and performance-table output. Apache NetBeans itself was not installed on the VPS; Maven reactor build, resources, JavaFX launch, and `nbactions.xml` were verified instead, so full NetBeans GUI opening remains the only unperformed verification step.
