#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
mvn dependency:build-classpath -Dmdep.outputFile=/tmp/monitoring-cp.txt -q
exec java -cp "target/classes:$(cat /tmp/monitoring-cp.txt)" com.healthgrid.monitoring.MonitoringServiceApplication "$@"
