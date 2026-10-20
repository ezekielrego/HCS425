#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# This is a separate stress experiment. It is not mixed with the primary
# fixed-total 1/2/5/10/20-client assignment comparison.
java -Dhcs425.results=results/csv/stress-performance-results.csv \
  -Dhcs425.clients=1,50,100 \
  -Dhcs425.operations=10000 \
  -Dhcs425.workers=128 \
  -cp "benchmark/target/classes:client/target/classes:server/target/classes:shared/target/classes" \
  zw.co.techinn.hcs425.benchmark.BenchmarkRunner

python3 scripts/svg_charts.py results/csv/stress-performance-results.csv results/charts/stress
