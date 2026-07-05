#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

STACK_NAME="${STACK_NAME:-CognitoM2mLocalDemoStack}"
REGION="${AWS_REGION:-${CDK_DEFAULT_REGION:-us-east-1}}"

outputs_json="$(aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --query 'Stacks[0].Outputs' \
  --output json)"

get_output() {
  local key="$1"
  echo "$outputs_json" | jq -r ".[] | select(.OutputKey == \"$key\") | .OutputValue"
}

USER_POOL_ID="$(get_output CognitoUserPoolId)"
CLIENT_ID="$(get_output CognitoPrimaryClientId)"

CLIENT_SECRET="$(aws cognito-idp describe-user-pool-client \
  --region "$REGION" \
  --user-pool-id "$USER_POOL_ID" \
  --client-id "$CLIENT_ID" \
  --query 'UserPoolClient.ClientSecret' \
  --output text)"

cat > .env.local <<EOF
COGNITO_REGION=$REGION
COGNITO_USER_POOL_ID=$USER_POOL_ID
COGNITO_DOMAIN_URL=$(get_output CognitoDomainUrl)
COGNITO_TOKEN_URL=$(get_output CognitoTokenUrl)
COGNITO_ISSUER_URI=$(get_output CognitoIssuerUri)
COGNITO_JWKS_URI=$(get_output CognitoJwksUri)
COGNITO_PRIMARY_CLIENT_ID=$CLIENT_ID
COGNITO_PRIMARY_CLIENT_SECRET=$CLIENT_SECRET
COGNITO_SCOPE=$(get_output CognitoScope)
SECONDARY_BASE_URL=http://localhost:8082
EOF

echo "Wrote .env.local"
