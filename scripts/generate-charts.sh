#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/svg_charts.py results/csv/performance-results.csv results/charts
