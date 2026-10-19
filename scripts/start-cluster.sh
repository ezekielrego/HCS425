#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -DskipTests package
mkdir -p logs
java -Dhcs425.node=B -Dhcs425.role=replica -Dhcs425.port=5051 -cp "replication/target/classes:client/target/classes:server/target/classes:shared/target/classes" zw.co.techinn.hcs425.replication.ReplicationNodeMain >logs/node-B.log 2>&1 & b_pid=$!
java -Dhcs425.node=C -Dhcs425.role=replica -Dhcs425.port=5052 -cp "replication/target/classes:client/target/classes:server/target/classes:shared/target/classes" zw.co.techinn.hcs425.replication.ReplicationNodeMain >logs/node-C.log 2>&1 & c_pid=$!
java -Dhcs425.node=A -Dhcs425.role=leader -Dhcs425.port=5050 -Dhcs425.replicas=127.0.0.1:5051:B,127.0.0.1:5052:C -cp "replication/target/classes:client/target/classes:server/target/classes:shared/target/classes" zw.co.techinn.hcs425.replication.ReplicationNodeMain >logs/node-A.log 2>&1 & a_pid=$!
printf 'Cluster started: A=%s B=%s C=%s\n' "$a_pid" "$b_pid" "$c_pid"
printf 'Ports: A 5050 (leader), B 5051, C 5052. Press Ctrl-C to stop.\n'
trap 'kill "$a_pid" "$b_pid" "$c_pid" 2>/dev/null || true' INT TERM EXIT
wait "$a_pid"
