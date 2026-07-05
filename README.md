# cognito-m2m-local-demo

Proof of concept: Angular → primary Spring Boot → secondary Spring Boot, with
**Amazon Cognito OAuth 2.0 client-credentials (M2M)** auth on the service hop.

```text
Angular (4200)  --X-Demo-User-->  primary (8081)  --Bearer JWT-->  secondary (8082)
                                       |                                 ^
                                       +--- client_credentials -> Cognito token
                                                                        |
                                            secondary validates JWT via Cognito JWKS
```

See [`spec/spec.md`](spec/spec.md) for the full design.

## Quick start

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

## Layout

| Path         | What it is                                            |
|--------------|-------------------------------------------------------|
| `infra/`     | AWS CDK (TypeScript) — Cognito user pool + M2M client |
| `primary/`   | Spring Boot, UI-facing, gets Cognito token, calls secondary |
| `secondary/` | Spring Boot, protected resource server, validates JWT |
| `ui/`        | Angular single page, dummy login                      |
| `scripts/`   | Shell wrappers behind the Makefile                    |

## Requirements

- Java 17+ (tested on 21), Maven (a wrapper is generated on first run)
- Node 20+ and npm
- AWS CLI v2 + `jq` for `export-env`, and a bootstrapped AWS account for `deploy`

Phase 1 proves service-to-service authorization only. Phase 2 (browser OAuth from
Angular to primary) is described in the spec but not implemented here.
