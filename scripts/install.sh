#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Installing CDK deps..."
( cd infra && npm install )

echo "Installing UI deps..."
( cd ui && npm install )

echo "Done. Java deps are fetched on first 'make run-primary'/'run-secondary'."
