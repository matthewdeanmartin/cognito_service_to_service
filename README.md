# cognito-m2m-local-demo

[Full docs](https://cognito-service-to-service.readthedocs.io/en/latest/)

Proof of concept: Angular → primary Spring Boot → secondary Spring Boot, with
**Amazon Cognito OAuth 2.0 client-credentials (M2M)** auth on the service hop.

```text
Angular (4200)  --X-Demo-User-->  primary (8081)  --Bearer JWT-->  secondary (8082)
                                       |                                 ^
                                       +--- client_credentials -> Cognito token
                                                                        |
                                            secondary validates JWT via Cognito JWKS
```

See [`spec/spec.md`](spec/spec.md) for the full design, or the
[**docs**](docs/index.md) for a concept-first explanation — start with
[Client Credentials, Explained](docs/concepts/client-credentials.md) if the M2M
auth is the confusing part.

Build the docs locally with `make docs-serve` (uses `uv` + a `.venv`; requires
[uv](https://docs.astral.sh/uv/)), or directly:

```bash
uv sync              # create .venv and install mkdocs (from pyproject.toml)
uv run mkdocs serve  # live-reload preview at http://localhost:8000
```

## Provisioning Cognito

You need a user pool, resource server (`secondary/read`), and an M2M app client.
There are three ways to create them — pick based on what you have:

| How | Command | Needs |
|-----|---------|-------|
| **CDK** (source of truth) | `make deploy && make export-env` | AWS creds + bootstrapped account |
| **boto3 script** (real AWS) | `make cognito-setup COGNITO_ENDPOINT_URL=` | AWS creds |
| **boto3 + local emulator** | see [No AWS required](#no-aws-required) below | Docker or `uv` |

All three write the same `.env.local` that the Spring services read at startup.
The boto3 script (`scripts/cognito_setup.py`) is idempotent — `setup` skips what
already exists, and there's `status` / `teardown`.

## Quick start (with AWS)

```bash
# 1. Provision Cognito (needs AWS creds + CDK bootstrapped account)
make install
make deploy
make export-env        # writes .env.local

# 2. Run the services (three terminals)
make run-secondary     # :8082
make run-primary       # :8081
make run-ui            # :4200
```

Then open http://localhost:4200, click **Login as demo-user**, then **Call primary**.

Smoke test without the UI:

```bash
make test-demo
```

## No AWS required

Two local emulators let you run without an AWS account. They're complementary —
see [the emulator debrief](docs/running/emulators-lessons-learned.md) for the
full story.

**robotocore** (Docker) has a real Cognito `/oauth2/token`, so the whole chain
runs unmodified:

```bash
make run-robotocore                                        # container on :4566
make cognito-setup COGNITO_ENDPOINT_URL=http://localhost:4566
# robotocore tokens aren't cryptographically signed, so run secondary in
# demo mode to accept them (still enforces expiry + scope):
SECURITY_SKIP_SIGNATURE=true make run-secondary
make run-primary
```

**moto** (pure Python, via `uv`) mints *verifiable* tokens but has no token
endpoint, so it needs the token/JWKS shim described in the docs:

```bash
make run-moto                                              # moto on :5111
make cognito-setup COGNITO_ENDPOINT_URL=http://localhost:5111
```

Details and trade-offs: [Testing with moto](docs/running/testing-with-moto.md),
[Testing with robotocore](docs/running/testing-with-robotocore.md).

## Layout

| Path         | What it is                                            |
|--------------|-------------------------------------------------------|
| `infra/`     | AWS CDK (TypeScript) — Cognito user pool + M2M client |
| `primary/`   | Spring Boot, UI-facing, gets Cognito token, calls secondary |
| `secondary/` | Spring Boot, protected resource server, validates JWT |
| `ui/`        | Angular single page, dummy login                      |
| `scripts/`   | `cognito_setup.py` (boto3 provisioner) + shell wrappers behind the Makefile |
| `docs/`      | MkDocs site (`pyproject.toml` + `uv` build the docs)  |

## Requirements

- Java 17+ (tested on 21), Maven (a wrapper is generated on first run)
- Node 20+ and npm
- [uv](https://docs.astral.sh/uv/) — runs the boto3 script and builds the docs
- **One provisioning path:**
    - CDK: AWS CLI v2 + `jq` + a bootstrapped AWS account, or
    - Docker (for robotocore), or
    - just `uv` (for moto)

Phase 1 proves service-to-service authorization only. Phase 2 (browser OAuth from
Angular to primary) is described in the spec but not implemented here.
