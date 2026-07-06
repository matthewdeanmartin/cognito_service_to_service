# Setup & Runbook

## Prerequisites

- **Java 17+** (tested on 21) and **Maven**
- **Node 20+** and **npm**
- **AWS CLI v2** and **jq** (for `export-env`)
- A **bootstrapped AWS account** with credentials configured (for `deploy`)

Every `make` target just calls a script in `scripts/`, so non-Make users can run
the same commands directly.

## One-time: provision Cognito

```bash
make install       # installs CDK + UI npm deps
make deploy        # cdk deploy — creates the Cognito user pool, etc.
make export-env    # writes .env.local from the stack outputs
```

`make export-env` produces `.env.local`, which the Spring services read at
startup. It contains the issuer, token URL, client id/secret, and scope — see
[Cognito Building Blocks](../concepts/cognito-building-blocks.md) for what each
value is.

!!! warning "`.env.local` holds a secret"
    It contains `COGNITO_PRIMARY_CLIENT_SECRET`. It's git-ignored. In
    production this would live in AWS Secrets Manager / SSM Parameter Store /
    Vault, never on disk.

## Run the three services

Use three terminals:

```bash
# Terminal 1
make run-secondary   # :8082

# Terminal 2
make run-primary     # :8081

# Terminal 3
make run-ui          # :4200
```

Then open **http://localhost:4200**, click **Login as demo-user**, then
**Call primary**. Expected:

```text
Logged in as demo-user
Primary: primary says hello
Secondary: secondary says hello
```

## Smoke test without the browser

```bash
make test-demo
```

Expected JSON:

```json
{
  "user": "demo-user",
  "primary": "primary says hello",
  "secondaryLabel": "secondary says hello"
}
```

## Tear down

```bash
make destroy       # cdk destroy — removes the Cognito stack
make clean         # removes local build outputs
```

## All targets

| Target              | Does                                     |
|---------------------|------------------------------------------|
| `make install`      | Install CDK/UI dependencies              |
| `make deploy`       | Deploy the Cognito stack                 |
| `make export-env`   | Write `.env.local` from stack outputs    |
| `make run-secondary`| Run secondary (:8082)                    |
| `make run-primary`  | Run primary (:8081)                      |
| `make run-ui`       | Run the Angular UI (:4200)               |
| `make test-demo`    | Smoke-test primary → secondary           |
| `make destroy`      | Destroy the Cognito stack                |
| `make clean`        | Clean local build outputs                |

Don't want to touch AWS at all? You can exercise the services against a fake
Cognito — see [Testing Without AWS (moto)](testing-with-moto.md).
