# Local AWS Emulators: Lessons Learned

This page collects what we actually learned wiring **moto** and **robotocore**
into this project to test Cognito M2M auth locally — the gotchas, the surprises,
and how to pick between them. It's the practical debrief; the how-to lives in
[Testing With moto](testing-with-moto.md) and
[Testing With robotocore](testing-with-robotocore.md).

## The one-paragraph summary

Neither emulator does everything. **moto** can mint a *cryptographically valid*
Cognito token but has **no token endpoint**, so `primary` can't fetch one
normally. **robotocore** has a **real `/oauth2/token`** (so the full flow runs
unmodified) but its tokens carry a **placeholder signature**, so a strict
resource server rejects them. Use each for the half it does well, and keep real
AWS as the source of truth.

## Capability matrix

| Capability                                      | moto | robotocore | real AWS |
|-------------------------------------------------|:----:|:----------:|:--------:|
| Provision pool / resource server / client / domain | ✓ | ✓ | ✓ |
| Idempotent boto3 setup/skip/teardown            |  ✓   |    ✓       |    ✓     |
| `/oauth2/token` client-credentials endpoint     |  ✗   |    ✓       |    ✓     |
| OIDC discovery (`/.well-known/openid-configuration`) | ✗ | ✓      |    ✓     |
| JWKS endpoint                                    |  ✓   |    ✓       |    ✓     |
| **Signature that actually verifies**            |  ✓*  |    ✗       |    ✓     |
| Full `primary → token → secondary` chain, unmodified | ✗ | ✓**   |    ✓     |

\* Only via moto's backend `create_jwt`, not via a token endpoint.
\** Reaches `200` only with `SECURITY_SKIP_SIGNATURE=true` on secondary.

## What surprised us

### moto has no token endpoint at all

moto emulates the Cognito **control plane** (create pool, client, resource
server) and serves a **JWKS**, but it does **not** implement the hosted-UI
OAuth2 `/oauth2/token` endpoint. That endpoint belongs to the Cognito *domain*,
which moto doesn't emulate. So there's nothing for `primary`'s
client-credentials POST to talk to.

**Workaround:** a tiny Flask app that (a) mints a moto-signed token via the
backend's `create_jwt`, and (b) re-serves moto's public JWKS + a discovery doc.
Details in [Testing With moto](testing-with-moto.md).

### moto's signing key is static and shared

moto ships a fixed RSA keypair (`kid=dummy`). Both the in-process backend and a
`moto_server` use it, so a token minted one way verifies against the JWKS served
the other way. Convenient — but it means the "security" is only structural.

### robotocore's tokens are not really signed

robotocore's `/oauth2/token` returns a token with a perfect claim set
(`alg: RS256`, correct `iss`, `aud`, `scope`, `exp`) — but the signature segment
is the literal string `fake-signature`, and the token's `kid` is random per call
and doesn't match the one key in robotocore's JWKS. So:

```text
401  "Signed JWT rejected: ... no matching key(s) found"
```

is the *correct* response from a strict resource server. This matched the
caution in the original robotocore handoff note exactly: it's great for API
compatibility and flow testing, but not proof of real JWT validation.

### issuer claim vs discovery issuer can disagree

robotocore's **discovery doc** advertises `issuer: http://localhost:4566`, but
the **token's `iss` claim** is the real AWS URL
(`https://cognito-idp.us-east-1.amazonaws.com/...`). Spring cross-checks these,
so even ignoring the signature, issuer validation is another mismatch to be
aware of. (Our skip-signature decoder sidesteps it by not running the issuer
validator.)

### Setting `issuer-uri` to empty does NOT disable issuer checking

A tempting-but-wrong idea: blank out `issuer-uri` to turn off issuer validation.
Spring instead builds a `JwtIssuerValidator("")` that rejects every real token.
If you need a token accepted, give it a *matching* issuer — don't try to disable
the check by emptying the config.

### Two constructors need `@Autowired`

Not emulator-specific, but found while running the packaged jars: `primary`'s
`CognitoTokenClient` has a production constructor and a test-only one. Spring
can't choose between two constructors and fails with *"No default constructor
found"* unless the production one is annotated `@Autowired`. **Unit tests missed
this; only running the real jar caught it.** Lesson: run the actual boot, not
just `mvn test`.

## Environment gotchas (Windows / this machine)

- **Port 5000 was already taken.** moto silently failed to bind ("socket
  forbidden") and a phantom service answered on 5000, producing confusing 404s.
  We moved moto to **5111**. Check what's on a port before blaming the tool.
- **The default `python` (3.14) couldn't import moto.** We installed it into the
  project venv via `uv` (`pyproject.toml` `test` group) and always invoke it
  with `uv run`.
- **Git-Bash `/c/...` paths break Windows Python.** Passing `/c/Users/...` to a
  native Python raises `FileNotFoundError`; use `C:/Users/...`.
- **Docker container vs host URLs differ.** From another container, robotocore is
  `http://robotocore:4566`; from the host workstation it's
  `http://localhost:4566`.

## How the tooling absorbs all this

The boto3 provisioner (`scripts/cognito_setup.py`) **auto-detects the backend**
by probing `/_robotocore/health`, then writes an `.env.local` tuned to it:

- **robotocore** → `COGNITO_TOKEN_URL` points at robotocore's real endpoint.
- **moto** → `COGNITO_TOKEN_URL` points at the Flask shim.
- **real AWS** → the normal hosted-domain token URL.

So the same `setup` / `status` / `teardown` commands work against all three, and
the services pick up the right URLs without you editing anything by hand.

## Recommended workflow

1. **Iterate on provisioning and the request flow** with robotocore — it's the
   closest to "it just runs" (`make run-robotocore` → `make cognito-setup ...`).
2. **Verify real signature/issuer/scope validation** with moto (strict
   `secondary`, no skip flag) — this is the security-critical path.
3. **Trust only real AWS Cognito** (the [CDK runbook](runbook.md)) as the final
   integration check before shipping.

Keep the endpoint URL injectable everywhere (it already is) so flipping between
the three is a one-line change, never a code change.
