#!/usr/bin/env bash
# Start a local robotocore container (LocalStack-style AWS emulator) that,
# unlike moto, serves a real Cognito /oauth2/token + JWKS + OIDC discovery.
# Listens on http://localhost:4566.
set -euo pipefail

NAME="${ROBOTOCORE_NAME:-robotocore}"
IMAGE="${ROBOTOCORE_IMAGE:-jackdanger/robotocore:latest}"

docker rm -f "$NAME" >/dev/null 2>&1 || true
echo "Starting robotocore ($IMAGE) on http://localhost:4566 ..."
docker run -d -p 4566:4566 --name "$NAME" "$IMAGE" >/dev/null

echo "Waiting for health..."
for _ in $(seq 1 30); do
  if curl -sf http://localhost:4566/_robotocore/health >/dev/null 2>&1; then
    echo "robotocore is healthy at http://localhost:4566"
    echo "Next: make cognito-setup COGNITO_ENDPOINT_URL=http://localhost:4566"
    exit 0
  fi
  sleep 1
done

echo "robotocore did not become healthy in time; check: docker logs $NAME" >&2
exit 1
