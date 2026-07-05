#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../infra"

npm install
npm run build
npx cdk deploy --require-approval never
