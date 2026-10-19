#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -DskipTests package
java -cp "benchmark/target/classes:client/target/classes:server/target/classes:shared/target/classes" zw.co.techinn.hcs425.benchmark.FaultToleranceHarness
