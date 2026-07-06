#!/usr/bin/env bash
# Start a local moto server (fake AWS) for the boto3 Cognito setup script.
# Default port 5111; override with MOTO_PORT.
set -euo pipefail
cd "$(dirname "$0")/.."

PORT="${MOTO_PORT:-5111}"
echo "Starting moto server on http://localhost:${PORT} (Ctrl-C to stop)..."
exec uv run moto_server -p "${PORT}"
