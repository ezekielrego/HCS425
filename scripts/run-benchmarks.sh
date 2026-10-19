#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -DskipTests package
mkdir -p results/csv results/charts
java -cp "benchmark/target/classes:client/target/classes:server/target/classes:shared/target/classes" \
  -Dhcs425.results=results/csv/performance-results.csv zw.co.techinn.hcs425.benchmark.BenchmarkRunner
"$(dirname "$0")/generate-charts.sh"
