# FAQ

## Is client credentials just a username and password?

The **client id + client secret** really is a credential pair, like a username
and password — but for a program. The difference is what you do with it: you
exchange it **once** for a short-lived, scoped **token**, and send the *token*
between services, never the secret. The full explanation is on
[Client Credentials, Explained](concepts/client-credentials.md).

## Why not just have primary send its password to secondary directly?

Because then the password rides on every request (more chances to leak),
secondary has to store and check it, and it carries no notion of *what* the
caller may do. A token is short-lived, verifiable offline, and scoped. See the
[trade-off discussion](concepts/client-credentials.md#the-naive-answer-and-why-we-dont-do-it).

## What's the difference between 401 and 403 here?

- **401 Unauthorized** — secondary can't establish *who* you are (no token, or a
  token that fails signature/issuer/expiry checks).
- **403 Forbidden** — it knows who you are, but your token lacks the required
  `secondary/read` scope.

## Does secondary call Cognito on every request?

No. It downloads Cognito's **public** keys (JWKS) once and verifies signatures
**offline** after that. Cognito is only contacted by *primary*, to get a token —
and even that is cached for ~an hour.

## Why does primary cache the token?

To avoid hammering the token endpoint. primary reuses the token until ~60s
before expiry. AWS recommends this for M2M workloads. The cache is a simple
in-memory `AtomicReference` in `CognitoTokenClient.java`.

## Is the Angular login real?

No — Phase 1's login is a **dummy flag**. Angular just sets `loggedIn = true`
and sends `X-Demo-User: demo-user`. primary uses that only as a display label,
not as security. Real user login is Phase 2.

## What does Phase 2 add?

Real user authentication from the browser to primary, using the
**authorization-code + PKCE** grant against Cognito's Hosted UI. primary then
becomes *both* a resource server (validating the user's token) *and* an OAuth
client (still getting its own M2M token to call secondary).

Crucially, the **primary → secondary hop stays M2M** — primary does *not*
forward the user's browser token to secondary. The service identity stays
separate from the user identity.

## Why does Phase 2 need a second app client?

Cognito **forbids** enabling the client-credentials grant on the same app client
as the authorization-code (browser) grant. So Phase 2 adds a *separate*
browser app client — no secret, PKCE, callback URLs — alongside the existing
M2M client.

## Why is there a two-constructor `@Autowired` thing in primary?

`CognitoTokenClient` has a production constructor and a test-only one. Spring
can't pick between two constructors, so the production one is annotated
`@Autowired`. Omitting it makes the app fail to start. Details in
[The Services in Detail](walkthrough/services.md#getting-and-caching-the-token).

## Can I run this without an AWS account?

For the security path, yes — use **moto** to fake Cognito. See
[Testing Without AWS](running/testing-with-moto.md). A real `cdk deploy` still
needs AWS credentials.

## Where do the config values come from?

`make deploy` provisions Cognito; `make export-env` writes the values into
`.env.local`. The mapping from Cognito resource → env var → service is in
[Cognito Building Blocks](concepts/cognito-building-blocks.md#where-the-values-end-up).
