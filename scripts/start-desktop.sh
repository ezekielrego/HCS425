#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -DskipTests install
if [[ -z "${DISPLAY:-}" ]]; then
  export DISPLAY=:99
  Xvfb :99 -screen 0 1280x800x24 -ac +extension GLX >/tmp/hcs425-xvfb.log 2>&1 &
  xvfb_pid=$!
  trap 'kill "$xvfb_pid" 2>/dev/null || true' EXIT
fi
exec mvn -q -pl desktop javafx:run -Dhcs425.embedded="${HCS425_EMBEDDED:-true}"
