#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Cleaning build outputs..."
rm -rf primary/target secondary/target
rm -rf ui/dist ui/.angular
rm -rf infra/cdk.out
find infra -maxdepth 2 -name '*.js' -not -path '*/node_modules/*' -delete 2>/dev/null || true
find infra -maxdepth 2 -name '*.d.ts' -not -path '*/node_modules/*' -delete 2>/dev/null || true
echo "Done. (node_modules and .env.local left intact)"
