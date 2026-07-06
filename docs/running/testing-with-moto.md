# Testing Without AWS (moto)

You can validate the entire security path — including real JWT signature
checking — **without deploying anything to AWS**, using
[moto](https://github.com/getmoto/moto) to stand in for Cognito.

This is how the services were verified end to end during development.

## Two levels of test

### Level 1 — the Java unit tests

These run out of the box, no AWS and no moto:

```bash
cd secondary && mvn test    # 401 / 403 / 200 scope checks (JwtDecoder mocked)
cd primary   && mvn test    # token fetch + caching (HTTP endpoint stubbed)
```

secondary's test uses Spring Security's `jwt()` test post-processor to fake an
authenticated token; primary's test uses a `MockWebServer` for the token
endpoint and asserts the token is fetched once and then served from cache.

### Level 2 — real signature validation with moto

The unit tests *mock away* the crypto. To exercise the **real** signature and
issuer validation, use moto, which:

- serves a **JWKS** endpoint backed by a static RSA keypair (`kid=dummy`), and
- can mint access tokens **signed** with the matching private key.

That's enough for secondary to verify tokens exactly as it would against real
Cognito.

## Provisioning without CDK: the boto3 script

`scripts/cognito_setup.py` is a non-CDK way to create the same Cognito resources
(user pool, resource server `secondary/read`, M2M app client with secret,
domain) and write `.env.local`. It's idempotent — each step **skips** what
already exists — and it works against either a local moto server or real AWS.

```bash
# 1. start a local fake-AWS server
make run-moto                       # moto on http://localhost:5111

# 2. drive the lifecycle (moto is the default endpoint)
make cognito-status                 # what exists right now
make cognito-setup                  # create anything missing, write .env.local
make cognito-setup                  # run again -> every step reports "skip"
make cognito-teardown               # delete the user pool
```

Under the hood these call:

```bash
uv run python scripts/cognito_setup.py {setup|status|teardown} \
    --endpoint-url http://localhost:5111
```

To point the **same script at real AWS**, drop the endpoint (it then uses your
normal AWS credentials):

```bash
make cognito-setup COGNITO_ENDPOINT_URL=
# or directly:
uv run python scripts/cognito_setup.py setup
```

!!! note "Why boto3 instead of the bash export script"
    The CDK stack + `scripts/export-cdk-outputs.sh` (bash) is the "real" path.
    The boto3 script is a lighter alternative for local iteration: idempotent
    build-up/skip/teardown in one place, no CloudFormation, and it runs against
    moto so you never touch AWS. Both produce the same `.env.local`.

!!! warning "moto has no token endpoint"
    The script provisions the control-plane resources, but moto does not serve
    the Cognito `/oauth2/token` hosted endpoint. So in moto mode it sets
    `COGNITO_TOKEN_URL` to the local Flask shim (`http://localhost:9999/...`)
    described below, which is what actually issues tokens in a full local run.

## How the moto rig works

moto does not emulate the OAuth2 `/oauth2/token` domain endpoint or the OpenID
discovery document, so a tiny Flask app fills those gaps:

```text
                 ┌─────────────────────────────────────────┐
                 │  Flask stub (localhost:9999)             │
   primary ─────▶│  POST /oauth2/token   → moto-signed JWT  │
                 │  GET  /.well-known/openid-configuration  │
   secondary ───▶│  GET  /.well-known/jwks.json  (moto key) │
                 └─────────────────────────────────────────┘
                         ▲
                         │ tokens minted by
                    moto's UserPool.create_jwt(...)
                    with scope + iss claims we control
```

Key tricks that make Spring accept the tokens:

- **Mint tokens whose `iss` points at the Flask stub.** moto's
  `create_jwt(..., extra_data={...})` merges `extra_data` *after* the default
  claims, so you can override both `scope` and `iss`.
- **Serve a matching discovery doc** whose `issuer` field equals the configured
  `issuer-uri` exactly. Spring cross-checks them.
- Configure secondary with `COGNITO_ISSUER_URI=http://localhost:9999` so its
  normal issuer-uri autoconfiguration (discovery → JWKS) just works.

!!! note "Gotcha: empty issuer-uri does not disable the check"
    Setting `issuer-uri` to an empty string doesn't turn off issuer validation —
    Spring builds a `JwtIssuerValidator("")` that then rejects every real token.
    Give it a *matching* issuer instead.

## Verified outcomes

Against moto-signed tokens, secondary produced all four expected results:

| Case                                   | Result |
|----------------------------------------|--------|
| no token                               | 401    |
| garbage token                          | 401    |
| valid signature, wrong scope           | 403    |
| valid signature, `secondary/read`      | 200 + label JSON |

And the full chain (`X-Demo-User → primary → token endpoint → secondary`)
returned the combined JSON, with the token endpoint hit only ~once across many
demo calls — confirming caching.

## Environment notes (Windows)

- moto wasn't importable under the machine's default `python` (3.14). Install it
  into a throwaway venv: `py -3.12 -m venv motovenv &&
  motovenv/Scripts/pip install "moto[cognitoidp,server]" flask`.
- Git-Bash `/c/...` paths break when passed to a Windows Python; use `C:/...`.

## Why not LocalStack / cdklocal for the CDK?

`cdklocal` targets **LocalStack**, not moto, and neither was needed here. The
CDK is verified structurally with `cdk synth` (correct CloudFormation template,
all Cognito resource types, all outputs present). A real `cdk deploy` still
needs live AWS credentials.
