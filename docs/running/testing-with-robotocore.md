# Testing With robotocore

[moto](testing-with-moto.md) can provision Cognito and mint *verifiable* tokens,
but it has **no token endpoint** — so `primary` can't actually POST for a token.
[robotocore](https://github.com/robotocore/robotocore) fills that gap: it's a
LocalStack-style emulator on a single endpoint (`http://localhost:4566`) whose
Cognito provider is **native** and serves a real `/oauth2/token`, JWKS, and
OIDC discovery document.

The trade-off is the mirror image of moto's — see
[Which emulator for what](#which-emulator-for-what) below.

## Quick start

```bash
make run-robotocore                                  # docker run, waits for health
make cognito-setup COGNITO_ENDPOINT_URL=http://localhost:4566
```

The setup script **auto-detects** robotocore (via its `/_robotocore/health`
endpoint) and writes an `.env.local` whose `COGNITO_TOKEN_URL`,
`COGNITO_ISSUER_URI`, and `COGNITO_JWKS_URI` all point at robotocore — no Flask
shim needed.

Or with Docker Compose:

```bash
docker compose -f docker-compose.robotocore.yml up -d
```

## Running the full chain

Because robotocore has a real token endpoint, `primary` works unmodified:

```bash
set -a; source .env.local; set +a

# secondary in demo mode (see the signature caveat below)
SECURITY_SKIP_SIGNATURE=true java -jar secondary/target/secondary-0.1.0.jar &
java -jar primary/target/primary-0.1.0.jar &

curl -s -H 'X-Demo-User: demo-user' http://localhost:8081/api/demo
```

```json
{
  "user": "demo-user",
  "primary": "primary says hello",
  "secondaryLabel": "secondary says hello"
}
```

That response is the real thing: `primary` POSTed client-credentials to
robotocore's `/oauth2/token`, got a token back, and used it to call `secondary`.

## The signature caveat (important)

robotocore's tokens have the correct **shape** — proper header, `iss`, `aud`,
`scope`, `exp` — but the **signature is a placeholder** (literally
`fake-signature`), and the token's random `kid` doesn't match the fixed demo key
in robotocore's JWKS.

So a **strict** resource server rightly rejects them:

```text
401  WWW-Authenticate: Bearer error="invalid_token",
     error_description="...Signed JWT rejected: ...no matching key(s) found"
```

This is expected and correct. To let the flow demo reach a `200`, `secondary`
has a **demo-only** switch, `SECURITY_SKIP_SIGNATURE=true`
(`InsecureJwtConfig.java`), that parses the token **without verifying the
signature**.

!!! danger "SECURITY_SKIP_SIGNATURE is for local flow demos only"
    Never enable it against real tokens — it disables the one check that proves
    a token is authentic. It still validates:

    - **expiry** — an expired token returns `401`
    - **scope** — a token lacking `secondary/read` returns `403`

    …so authorization behavior is preserved; only signature verification is
    skipped. Verified behavior in this mode:

    | Case                                | Result |
    |-------------------------------------|--------|
    | no token                            | 401    |
    | malformed token                     | 401    |
    | expired (unsigned) token            | 401    |
    | valid-shape token, wrong scope      | 403    |
    | valid-shape token, `secondary/read` | 200    |

## Which emulator for what

| Capability                                   | moto | robotocore |
|----------------------------------------------|:----:|:----------:|
| Provision Cognito (pool / RS / client / domain) | ✓ | ✓ |
| `/oauth2/token` client-credentials endpoint  |  ✗   |    ✓       |
| OIDC discovery document                      |  ✗   |    ✓       |
| **Cryptographically verifiable token**       |  ✓*  |    ✗       |

\* moto has no token endpoint, but its backend can mint a *properly signed*
token whose signature verifies against moto's JWKS — see
[Testing With moto](testing-with-moto.md).

**Rules of thumb:**

- Use **robotocore** to exercise the real request/response **flow**
  (`primary` genuinely fetching a token) end to end.
- Use **moto** when you need a token that passes **real signature verification**
  (strict `secondary`, no skip flag).
- Use **real AWS Cognito** (the [CDK path](runbook.md)) as the integration
  source of truth. Keep the endpoint URL injectable so you can flip between all
  three.

## Teardown

```bash
make cognito-teardown COGNITO_ENDPOINT_URL=http://localhost:4566
make stop-robotocore
```
