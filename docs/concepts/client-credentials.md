# Client Credentials, Explained

> "I still don't get this client credential stuff... this is like username and
> passwords?"

Short answer: **the client id and client secret really are just a username and
password** — but for a *program*, not a person. The twist is what happens
*next*: the service doesn't send that password on every call. It trades it,
once, for a short-lived **token**, and sends the token instead.

Let's build up the intuition.

## The problem it solves

`primary` needs to call `secondary`. `secondary` shouldn't answer just anybody —
it needs proof the caller is allowed. There's no human here to type a password,
no browser to pop up a login screen. It's one server phoning another at 3 a.m.
with nobody watching.

So how does a machine prove who it is?

## The naive answer (and why we don't do it)

You *could* give primary a password and have it send that password to secondary
on every request:

```http
GET /api/label
X-Service-Password: hunter2      ← don't do this
```

This "works", but it's fragile:

- The password rides along on **every single request**. More chances to leak it
  in a log, a proxy, a crash dump.
- `secondary` now has to **know and store** primary's password to check it. Every
  service that primary talks to becomes a place the password lives.
- Rotating the password means updating it in many places at once.
- The password says *who you are* but nothing about *what you're allowed to do*.

## The client-credentials answer

Instead, we introduce a trusted third party — **Cognito** — that both services
already trust. The flow has two hops:

```text
Step 1 — prove identity ONCE, get a token
  primary ──(client_id + client_secret)──▶ Cognito
  primary ◀──────( access token )──────── Cognito

Step 2 — use the token to call secondary
  primary ──(Authorization: Bearer <token>)──▶ secondary
  secondary checks the token WITHOUT calling primary or Cognito's secret store
```

The `client_id` / `client_secret` pair is primary's "username and password".
But notice:

- primary shows that secret **only to Cognito**, and **only once** (then it
  caches the token). secondary never sees the secret.
- What primary sends to secondary is a **token** — a signed, time-limited,
  scoped permission slip. If it leaks, it expires on its own in an hour.
- `secondary` verifies the token **cryptographically, offline**. It does not
  store primary's password and does not have to phone anyone on each request.

!!! tip "The one-line mental model"
    Client id + secret = a **password you show once to the bouncer (Cognito)**.
    The token = the **wristband** the bouncer gives you. You flash the wristband
    at every door after that. Doors can check a wristband is genuine without
    calling the bouncer.

## "Client credentials" is a *grant type*

OAuth 2.0 has several ways to get a token, called **grants**. They differ by
*who is present*:

| Grant                     | Who's involved                 | Used for                        |
|---------------------------|--------------------------------|---------------------------------|
| Authorization Code + PKCE | A human, in a browser          | User login (Phase 2 here)       |
| **Client Credentials**    | **No human — one app to another** | **Service-to-service (this demo)** |
| Refresh Token             | Renewing an existing session   | Keeping users logged in         |

**Client credentials** is the one with *no user*. The application authenticates
*as itself*. That's exactly primary's situation: it's acting as the "primary
service", not on behalf of any particular person.

This is why AWS documents client credentials as the machine-to-machine (M2M)
grant, and why we use it here.

## What primary actually sends to Cognito

A plain HTTPS form POST to Cognito's token endpoint:

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id=<the primary app client id>
&client_secret=<the primary app client secret>
&scope=secondary/read
```

And Cognito answers:

```json
{
  "access_token": "eyJ...<a JWT>...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

That `access_token` is the wristband. The next page,
[Tokens, Scopes & Validation](tokens-and-scopes.md), opens it up and shows how
secondary checks it.

## So, is it like username and password?

**Yes and no**, and the "no" is the whole point:

- **Yes:** `client_id` + `client_secret` is a credential pair, conceptually a
  username and password for a program.
- **No:** you don't authenticate with it on every call. You exchange it **once**
  for a **token**, and that token — not the password — is what travels between
  services. The token is short-lived, scoped to a specific permission, and
  verifiable by anyone without contacting Cognito.

That indirection is what makes it safer and more scalable than passing a shared
password around.
