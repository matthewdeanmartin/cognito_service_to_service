#!/usr/bin/env bash
set -euo pipefail

curl -sS \
  -H 'X-Demo-User: demo-user' \
  http://localhost:8081/api/demo | jq .
