Here’s the handoff I’d give the other LLM.

Robotocore is not started like `moto_server`. Treat it more like a **single LocalStack-style AWS endpoint on port 4566**. The official quick start is:

```bash
docker run -d -p 4566:4566 --name robotocore jackdanger/robotocore:latest
# alternate image:
# docker run -d -p 4566:4566 --name robotocore ghcr.io/robotocore/robotocore:latest
```

Verify:

```bash
curl -s http://localhost:4566/_robotocore/health
curl -s http://localhost:4566/_robotocore/config
```

Robotocore’s README says to point AWS SDKs/CLI at `http://localhost:4566`; it also says the 12-digit access key ID is interpreted as the local account ID. It lists Cognito User Pools as a **native provider** with “JWT tokens, user management, triggers,” while many other services are Moto-backed. ([GitHub][1])

### “Instead of moto server” mapping

Old Moto server-ish pattern:

```bash
moto_server -H 0.0.0.0 -p 5000
```

Robotocore replacement:

```bash
docker rm -f robotocore 2>/dev/null || true
docker run -d -p 4566:4566 --name robotocore jackdanger/robotocore:latest
```

Moto’s standalone server mode is a generic HTTP endpoint for AWS SDKs, usually on port 5000 unless changed. ([Moto Documentation][2]) Robotocore instead standardizes on `localhost:4566` and presents one AWS-like endpoint for all supported services. ([GitHub][1])

### Environment variables for AWS CLI / SDKs

Use these in Git Bash / Linux shell:

```bash
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_ACCESS_KEY_ID=123456789012
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
```

Then normal AWS CLI calls should hit robotocore:

```bash
aws sts get-caller-identity
aws cognito-idp create-user-pool --pool-name demo-pool
```

Robotocore’s own AGENTS.md says environment variables are the “no code changes” path and that explicit `endpoint_url` is preferred for tests. ([GitHub][3])

PowerShell version:

```powershell
$env:AWS_ENDPOINT_URL="http://localhost:4566"
$env:AWS_ACCESS_KEY_ID="123456789012"
$env:AWS_SECRET_ACCESS_KEY="test"
$env:AWS_DEFAULT_REGION="us-east-1"
```

### boto3 pattern

```python
import boto3

AWS = {
    "endpoint_url": "http://localhost:4566",
    "aws_access_key_id": "123456789012",
    "aws_secret_access_key": "test",
    "region_name": "us-east-1",
}

cognito = boto3.client("cognito-idp", **AWS)

pool = cognito.create_user_pool(PoolName="demo-pool")
print(pool)
```

### Java / Spring Boot pattern

For AWS SDK for Java v2, configure the client with endpoint override:

```java
CognitoIdentityProviderClient cognito = CognitoIdentityProviderClient.builder()
    .endpointOverride(URI.create("http://localhost:4566"))
    .region(Region.US_EAST_1)
    .credentialsProvider(
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create("123456789012", "test")
        )
    )
    .build();
```

For local runs, make the endpoint configurable:

```properties
aws.endpoint-url=http://localhost:4566
aws.region=us-east-1
aws.access-key-id=123456789012
aws.secret-access-key=test
```

Then wire that into the SDK builder only for the local profile.

### Docker Compose version

```yaml
services:
  robotocore:
    image: jackdanger/robotocore:latest
    container_name: robotocore
    ports:
      - "4566:4566"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:4566/_robotocore/health"]
      interval: 5s
      retries: 10

  primary:
    build: ./primary
    environment:
      AWS_ENDPOINT_URL: http://robotocore:4566
      AWS_ACCESS_KEY_ID: "123456789012"
      AWS_SECRET_ACCESS_KEY: test
      AWS_DEFAULT_REGION: us-east-1
```

Important Docker gotcha: from another container, use `http://robotocore:4566`; from the host workstation, use `http://localhost:4566`.

### Cognito smoke test commands

This is the minimal thing I’d ask the LLM to implement first:

```bash
aws cognito-idp create-user-pool \
  --pool-name demo-pool
```

Capture the pool ID:

```bash
POOL_ID=$(aws cognito-idp list-user-pools \
  --max-results 10 \
  --query 'UserPools[?Name==`demo-pool`].Id | [0]' \
  --output text)
```

Create a resource server:

```bash
aws cognito-idp create-resource-server \
  --user-pool-id "$POOL_ID" \
  --identifier "https://secondary.local" \
  --name "secondary" \
  --scopes ScopeName="read",ScopeDescription="Read from secondary"
```

Create an app client:

```bash
aws cognito-idp create-user-pool-client \
  --user-pool-id "$POOL_ID" \
  --client-name primary-service \
  --generate-secret \
  --allowed-o-auth-flows client_credentials \
  --allowed-o-auth-scopes "https://secondary.local/read" \
  --allowed-o-auth-flows-user-pool-client
```

Create a domain:

```bash
aws cognito-idp create-user-pool-domain \
  --user-pool-id "$POOL_ID" \
  --domain demo-local-domain
```

Then describe them:

```bash
aws cognito-idp describe-user-pool --user-pool-id "$POOL_ID"
aws cognito-idp list-resource-servers --user-pool-id "$POOL_ID"
aws cognito-idp list-user-pool-clients --user-pool-id "$POOL_ID"
aws cognito-idp describe-user-pool-domain --domain demo-local-domain
```

### What I would tell the LLM to be careful about

Do **not** assume the hosted OAuth URL is identical to AWS Cognito’s real hosted domain. Robotocore’s docs clearly say Cognito User Pools are native and support JWT/user-management behavior, but I did not find a first-party example proving the full Cognito Hosted UI/client-credentials `/oauth2/token` endpoint shape. ([GitHub][1])

So: use robotocore for **local AWS API compatibility and smoke tests**, but keep the real AWS Cognito CDK path as the integration truth. For the PoC, make the endpoint URL injectable so you can flip between:

```text
local: http://localhost:4566
real:  no endpoint override
```

[1]: https://github.com/robotocore/robotocore "GitHub - robotocore/robotocore · GitHub"
[2]: https://docs.getmoto.org/en/4.1.12/docs/server_mode.html?utm_source=chatgpt.com "Non-Python SDK's / Server Mode"
[3]: https://github.com/robotocore/robotocore/blob/main/AGENTS.md "robotocore/AGENTS.md at main · robotocore/robotocore · GitHub"
