#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -DskipTests package
exec java -Dhcs425.host="${HCS425_HOST:-127.0.0.1}" -Dhcs425.port="${HCS425_PORT:-5050}" -Dhcs425.workers="${HCS425_WORKERS:-32}" -cp "server/target/classes:shared/target/classes" zw.co.techinn.hcs425.server.ServerMain
