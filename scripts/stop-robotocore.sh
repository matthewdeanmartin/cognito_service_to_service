#!/usr/bin/env bash
# Stop and remove the local robotocore container.
set -euo pipefail
NAME="${ROBOTOCORE_NAME:-robotocore}"
docker rm -f "$NAME" >/dev/null 2>&1 && echo "Removed $NAME" || echo "No $NAME container running"
