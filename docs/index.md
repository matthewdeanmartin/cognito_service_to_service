# Cognito M2M Local Demo

A small, runnable proof of concept that shows **one backend service securely
calling another** using Amazon Cognito — the same pattern you'd use in
production, shrunk down so every moving part is visible.

```text
Angular UI  ──X-Demo-User──▶  primary  ──Bearer JWT──▶  secondary
(:4200)                       (:8081)                    (:8082)
                                 │                           ▲
                                 │ client_credentials        │ validates the
                                 └──────▶ Cognito ───────────┘ token's signature
                                          (issues token)        and scope
```

## What this demonstrates

1. A browser does a **fake** login (no real auth yet — that's Phase 2).
2. Angular calls the **primary** Spring Boot service.
3. primary gets a token from **Cognito** and uses it to call **secondary**.
4. secondary **verifies** that token before answering.

The interesting part is step 3 and 4: how does primary prove to secondary that
it's allowed to call it, *without a human being involved*? That's what the
**client credentials** grant is for.

## Start here

New to this? Read the concepts in order — they build on each other:

1. [**Client Credentials, Explained**](concepts/client-credentials.md) — the
   "is this just username and password?" question, answered.
2. [Tokens, Scopes & Validation](concepts/tokens-and-scopes.md) — what the token
   actually is and how secondary checks it.
3. [Cognito Building Blocks](concepts/cognito-building-blocks.md) — the AWS
   pieces (user pool, resource server, app client) and how they map to the demo.

Then see how they fit together in the [Architecture](architecture.md) and the
[click-by-click Walkthrough](walkthrough/request-lifecycle.md).

Want to just run it? Jump to the [Runbook](running/runbook.md).

!!! note "Scope of this PoC"
    This is **Phase 1**: it proves *service-to-service* authorization only.
    The browser login is a stand-in. Real user login from Angular is Phase 2,
    sketched in the [FAQ](faq.md) but not implemented here.
