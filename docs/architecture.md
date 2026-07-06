# Architecture

## The whole picture

```text
+-------------------+
| Angular UI        |
| localhost:4200    |
| Dummy login only  |
+---------+---------+
          |
          | GET /api/demo
          | X-Demo-User: demo-user
          v
+-------------------+
| primary  (JSB)    |
| localhost:8081    |
|                   |
| Gets Cognito      |
| access token via  |
| client_credentials|
+---------+---------+
          |
          | GET /api/label
          | Authorization: Bearer <access_token>
          v
+-------------------+        +------------------------+
| secondary (JSB)   |        | AWS Cognito            |
| localhost:8082    |<------>| User Pool              |
|                   |  JWKS  | App Client             |
| validates JWT     |        | Resource Server/Scopes |
+-------------------+        +------------------------+
```

JSB = Java Spring Boot.

## The three trust boundaries

It helps to see this as **three separate trust relationships**, not one:

1. **Browser → primary** — *not real security in Phase 1.* Angular sends
   `X-Demo-User: demo-user`, a plain header. primary treats it as a display
   label only. This is the placeholder that Phase 2 replaces with real user
   OAuth.

2. **primary → Cognito** — *real.* primary proves it's the "primary service"
   with its `client_id` + `client_secret` and receives a scoped access token.

3. **primary → secondary** — *real.* primary presents the token; secondary
   validates its signature, issuer, expiry, and scope before answering.

!!! note "Why the browser token is *not* forwarded to secondary"
    Even in Phase 2, primary does **not** pass the user's browser token on to
    secondary. The primary→secondary hop uses primary's **own service identity**
    (the M2M token). The service boundary stays explicit: "primary the service"
    is calling secondary, regardless of which user triggered it.

## Component responsibilities

| Component     | Port | Responsibility                                                  |
|---------------|------|-----------------------------------------------------------------|
| **ui**        | 4200 | One page. Fake login, one button, shows the result or an error. |
| **primary**   | 8081 | Accepts the UI call, gets & **caches** a Cognito token, calls secondary, returns combined JSON. |
| **secondary** | 8082 | Protected resource server. Validates the JWT + scope, returns a label. |
| **infra**     |  —   | CDK (TypeScript) that provisions the Cognito user pool, resource server, app client, and domain. |

## Token caching

primary does **not** fetch a new token on every request. It caches the token in
memory and reuses it until ~60 seconds before expiry. AWS recommends this — it
cuts load on the token endpoint and matters as M2M traffic scales. See
`CognitoTokenClient.java`; the [walkthrough](walkthrough/request-lifecycle.md)
traces both the cache-miss and cache-hit paths.

## Technology choices

- **Spring Boot 3 + Spring Security** resource server for JWT validation — the
  issuer-uri config auto-discovers the JWKS and wires up all the checks.
- **RestClient** (Spring's modern synchronous HTTP client) for both the token
  fetch and the secondary call.
- **AWS CDK** in TypeScript for infrastructure as code.
- **Angular** standalone components (no NgModules) for the UI.
