# Tokens, Scopes & Validation

The [previous page](client-credentials.md) ended with primary holding an
`access_token`. This page opens that token up: what's inside it, and how
`secondary` decides to trust it.

## The token is a JWT

The access token is a **JWT** (JSON Web Token) — three
base64 chunks separated by dots: `header.payload.signature`.

Decode the middle chunk and you get plain JSON claims. A Cognito M2M access
token looks roughly like this:

```json
{
  "iss": "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_abc123",
  "client_id": "the-primary-app-client-id",
  "token_use": "access",
  "scope": "secondary/read",
  "iat": 1783297256,
  "exp": 1783300856
}
```

Key claims:

`iss` (issuer)
: Who minted the token. secondary is configured to trust exactly this issuer.

`scope`
: **What the bearer is allowed to do.** Here: read from secondary. This is the
  authorization decision baked into the token.

`exp` (expiry)
: When the token stops being valid. About an hour out. This is why a leaked
  token is a limited problem — it dies on its own.

`signature`
: A cryptographic signature over the header + payload, made with Cognito's
  **private** key.

## Why the signature matters

Anyone can *write* JSON claiming `scope: secondary/read`. What they **can't** do
is forge the signature — that requires Cognito's private key, which never leaves
AWS.

secondary verifies the signature using Cognito's **public** key. That public key
is published at a well-known URL called the **JWKS** (JSON Web Key Set):

```text
https://cognito-idp.<region>.amazonaws.com/<pool-id>/.well-known/jwks.json
```

```text
Cognito (has PRIVATE key) ──signs──▶  token
                                        │
secondary (fetches PUBLIC key from JWKS) ──verifies signature──▶ trust / reject
```

This is public-key cryptography: signing needs the secret half, verifying only
needs the public half. So secondary can validate any token **offline**, without
calling Cognito on every request and without holding any secret.

## What "validation" actually checks

When a request arrives with `Authorization: Bearer <token>`, secondary's
Spring Security resource server runs a checklist:

1. **Is it a well-formed JWT?** If not → `401 Unauthorized`.
2. **Is the signature genuine?** Verified against the JWKS public key.
   Tampered or fake → `401`.
3. **Is it expired?** Past `exp` → `401`.
4. **Is the issuer the one we trust?** `iss` must match the configured
   Cognito issuer → otherwise `401`.
5. **Does it have the required scope?** The `/api/label` endpoint requires
   `secondary/read`. A valid token *without* that scope → `403 Forbidden`.

!!! note "401 vs 403 — a useful distinction"
    - **401 Unauthorized** = "I don't know who you are." No token, or a token
      that fails verification.
    - **403 Forbidden** = "I know who you are, but you're not allowed to do
      *this*." A perfectly valid token that lacks the needed scope.

    The demo's tests assert exactly these four outcomes.

## Scopes = fine-grained permissions

A scope is just a string that names a permission. In this demo there's one:
`secondary/read`. It's built from two Cognito pieces:

- a **resource server** identified as `secondary` (think: the API being
  protected), and
- a **scope** named `read` on it.

Cognito joins them with a slash → `secondary/read`.

If secondary later grew a write endpoint, you'd add a `write` scope, mint tokens
with `secondary/write`, and gate that endpoint on it — without touching
passwords or the signature machinery at all. That composability is a big reason
scopes beat "one shared password per service".

## How this maps to the code

- **primary** requests the token with `scope=secondary/read`
  (`CognitoTokenClient.java`).
- **secondary** requires that authority on the endpoint
  (`SecurityConfig.java`): `.hasAuthority("SCOPE_secondary/read")`.
- Spring turns the token's `scope` claim into an authority prefixed with
  `SCOPE_`, which is why the config string looks the way it does.

Next: the [Cognito Building Blocks](cognito-building-blocks.md) that produce all
of this.
