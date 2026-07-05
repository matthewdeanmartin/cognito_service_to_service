#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

set -a
source .env.local
set +a

cd secondary
if [ -x ./mvnw ]; then
  ./mvnw spring-boot:run
else
  mvn spring-boot:run
fi
