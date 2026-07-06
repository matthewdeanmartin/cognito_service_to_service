# What Happens on One Click

Follow a single click of **"Call primary"** all the way through the system and
back. This is where the concepts turn into a concrete sequence.

## The sequence

```text
Browser        primary          Cognito         secondary
   │              │                │                │
   │ GET /api/demo│                │                │
   │ X-Demo-User  │                │                │
   ├─────────────▶│                │                │
   │              │  need a token? │                │
   │              │  cache empty…  │                │
   │              │ POST /oauth2/token              │
   │              │ grant_type=client_credentials   │
   │              │ client_id/secret, scope         │
   │              ├───────────────▶│                │
   │              │  access_token  │                │
   │              │◀───────────────┤                │
   │              │ (cache it ~1h) │                │
   │              │                                 │
   │              │ GET /api/label                  │
   │              │ Authorization: Bearer <token>   │
   │              ├────────────────────────────────▶│
   │              │                │  validate JWT: │
   │              │                │  sig? iss? exp? │
   │              │                │  scope?         │
   │              │                │  (JWKS public   │
   │              │                │   key, offline) │
   │              │  {label: ...}  │                │
   │              │◀────────────────────────────────┤
   │ combined JSON│                │                │
   │◀─────────────┤                │                │
   │              │                │                │
```

## Step by step

### 1. Browser → primary

Angular's `DemoApiService` issues:

```http
GET http://localhost:8081/api/demo
X-Demo-User: demo-user
```

That header is the **dummy login** — a display label, not a credential. primary
reads it into the `user` field of the response and otherwise ignores it for
security.

### 2. primary needs a token (cache check)

`SecondaryClient` asks `CognitoTokenClient` for a token. The token client checks
its in-memory cache:

- **Cache hit** (token exists and isn't within 60s of expiry) → reuse it, skip
  to step 4. *This is the common case.*
- **Cache miss** (first call, or the token is about to expire) → fetch a new one
  in step 3.

### 3. primary → Cognito (only on a cache miss)

```http
POST <COGNITO_TOKEN_URL>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id=<COGNITO_PRIMARY_CLIENT_ID>
&client_secret=<COGNITO_PRIMARY_CLIENT_SECRET>
&scope=secondary/read
```

Cognito verifies the client id/secret and returns an `access_token` plus
`expires_in`. primary stores it with a computed expiry timestamp and moves on.

### 4. primary → secondary

```http
GET http://localhost:8082/api/label
Authorization: Bearer <access_token>
```

### 5. secondary validates (the security moment)

Spring Security's resource server runs the full checklist from
[Tokens & Scopes](../concepts/tokens-and-scopes.md): signature (via the JWKS
public key, **offline**), issuer, expiry, and the `secondary/read` scope. All
pass → the request reaches `LabelController`, which returns:

```json
{ "service": "secondary", "label": "secondary says hello" }
```

### 6. primary combines and responds

primary merges its own greeting with secondary's label:

```json
{
  "user": "demo-user",
  "primary": "primary says hello",
  "secondaryLabel": "secondary says hello"
}
```

### 7. Angular renders it

```text
Logged in as demo-user
Primary: primary says hello
Secondary: secondary says hello
```

## What each failure looks like

| If…                                   | secondary returns | UI shows           |
|---------------------------------------|-------------------|--------------------|
| primary sends no token                | 401               | error message      |
| the token is tampered/expired/bogus   | 401               | error message      |
| the token is valid but lacks the scope| 403               | error message      |
| everything is correct                 | 200               | the two labels     |

These are exactly the cases the automated tests assert — see
[Testing Without AWS](../running/testing-with-moto.md).
