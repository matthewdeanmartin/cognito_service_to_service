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
