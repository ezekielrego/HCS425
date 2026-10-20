#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/svg_charts.py "${1:-results/csv/performance-results.csv}" "${2:-results/charts}"
