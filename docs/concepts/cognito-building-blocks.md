# Cognito Building Blocks

Cognito has a lot of features. This demo uses only four of them. Here's each
one, in plain terms, and where it shows up in the CDK stack
(`infra/lib/cognito-m2m-stack.ts`).

## 1. User Pool

The **user pool** is the container for identities and the thing that issues and
signs tokens. Despite the name, in M2M we don't put any *users* in it — we use
it as the token authority. Its ID appears in the token's issuer URL.

```ts
const userPool = new cognito.UserPool(this, "UserPool", {
  userPoolName: "local-m2m-demo-pool",
  selfSignUpEnabled: false,
});
```

## 2. Resource Server

A **resource server** represents *the API you're protecting* — here, secondary.
You give it an identifier and declare the scopes (permissions) it offers.

```ts
const resourceServer = userPool.addResourceServer("SecondaryResourceServer", {
  identifier: "secondary",
  scopes: [{ scopeName: "read", scopeDescription: "Read labels from secondary" }],
});
```

Identifier `secondary` + scope `read` → the full scope string **`secondary/read`**.

## 3. App Client (the M2M client)

An **app client** is an application's identity within the pool. This is where
the `client_id` and `client_secret` — primary's "username and password" from the
[client credentials page](client-credentials.md) — come from.

```ts
const primaryClient = userPool.addClient("PrimaryServiceClient", {
  generateSecret: true,                    // ← creates the client_secret
  oAuth: {
    flows: { clientCredentials: true },    // ← enables the M2M grant
    scopes: [ /* secondary/read */ ],      // ← what tokens for this client may request
  },
});
```

Two things to notice:

- `generateSecret: true` is what makes this a *confidential* client with a
  secret. A browser app would set this to `false` (it can't keep a secret).
- `clientCredentials: true` opts this client into the M2M grant.

!!! warning "One client can't do both"
    Cognito **forbids** enabling client-credentials on the *same* app client as
    the browser (authorization-code) grant. That's why Phase 2's browser login
    needs a **separate** app client. See the [FAQ](../faq.md).

## 4. Cognito Domain

The **domain** gives you the hosted OAuth endpoints, most importantly the token
endpoint primary POSTs to:

```text
https://<domain-prefix>.auth.<region>.amazoncognito.com/oauth2/token
```

```ts
const domain = userPool.addDomain("Domain", {
  cognitoDomain: { domainPrefix },
});
```

## How they connect

```text
        User Pool  (issues & signs tokens, hosts JWKS)
          │
          ├── Resource Server "secondary"
          │       └── scope "read"   ─────────┐
          │                                   │  combine → "secondary/read"
          ├── App Client "primary"            │
          │       ├── client_id + secret      │
          │       ├── flow: client_credentials│
          │       └── allowed scope ◀─────────┘
          │
          └── Domain  →  /oauth2/token  (primary asks here)
```

- **primary** uses the *app client's* id/secret at the *domain's* token endpoint
  to get a token carrying the *resource server's* scope.
- **secondary** uses the *user pool's* issuer + JWKS to validate that token.

## Where the values end up

After `make deploy`, `make export-env` writes these into `.env.local`, which the
two Spring services read at startup:

| `.env.local` key                | Comes from            | Used by   |
|---------------------------------|-----------------------|-----------|
| `COGNITO_ISSUER_URI`            | User pool             | secondary |
| `COGNITO_TOKEN_URL`             | Domain                | primary   |
| `COGNITO_PRIMARY_CLIENT_ID`     | App client            | primary   |
| `COGNITO_PRIMARY_CLIENT_SECRET` | App client (fetched)  | primary   |
| `COGNITO_SCOPE`                 | Resource server scope | primary   |

!!! note "The secret is fetched separately"
    CDK's high-level construct doesn't expose the generated client secret as a
    stack output. `scripts/export-cdk-outputs.sh` fetches it afterward with
    `aws cognito-idp describe-user-pool-client`.
