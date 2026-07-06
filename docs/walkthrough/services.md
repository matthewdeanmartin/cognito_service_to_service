# The Services in Detail

A closer look at the code behind each hop. File paths are relative to the repo
root.

## secondary — the protected resource server

secondary's whole job is: *validate the token, then answer.* The validation is
almost entirely declarative.

### Turning on JWT validation

`secondary/src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${COGNITO_ISSUER_URI}
```

That single `issuer-uri` line makes Spring:

1. fetch the issuer's OpenID discovery document,
2. find the JWKS URL from it,
3. download the public keys, and
4. install validators for signature, issuer, and expiry.

You write no crypto code.

### Requiring a scope

`secondary/src/main/java/com/example/secondary/SecurityConfig.java`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/api/label").hasAuthority("SCOPE_secondary/read")
    .anyRequest().authenticated()
)
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

The `JwtAuthenticationConverter` bean maps the token's `scope` claim to
authorities with a `SCOPE_` prefix — which is why the required authority is
`SCOPE_secondary/read`.

### The endpoint itself

`LabelController.java` is deliberately trivial — by the time code runs here, the
request is already authorized:

```java
@GetMapping("/api/label")
public Map<String, String> label() {
    return Map.of("service", "secondary", "label", "secondary says hello");
}
```

## primary — the caller

primary has two jobs: **get a token** (and cache it) and **use it**.

### Getting and caching the token

`primary/src/main/java/com/example/primary/CognitoTokenClient.java` holds the
token in an `AtomicReference` and only refetches when it's missing or near
expiry:

```java
CachedToken existing = cachedToken.get();
if (existing != null && existing.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
    return existing.accessToken();   // cache hit
}
// …otherwise POST client_credentials to the token endpoint, then cache…
```

!!! note "A subtle wiring detail"
    This class has two constructors — the public one for production and a
    package-private one for injecting a stub HTTP client in tests. Spring can't
    choose between two constructors on its own, so the production one is marked
    `@Autowired`. Without it, the app fails to start with *"No default
    constructor found."* (This was a real bug caught by running the packaged
    jar, not by unit tests.)

### Using the token

`SecondaryClient.java` attaches it as a Bearer header:

```java
String token = tokenClient.getAccessToken();
restClient.get()
    .uri("/api/label")
    .headers(h -> h.setBearerAuth(token))
    .retrieve()
    .body(Map.class);
```

### Combining the response

`DemoController.java` reads the dummy user header and merges the labels:

```java
@GetMapping("/api/demo")
public Map<String, String> demo(
    @RequestHeader(name = "X-Demo-User", defaultValue = "anonymous") String demoUser) {
    String label = secondaryClient.getLabel();
    return Map.of("user", demoUser,
                  "primary", "primary says hello",
                  "secondaryLabel", label);
}
```

### CORS

Because the browser calls primary cross-origin (`:4200` → `:8081`),
`CorsConfig.java` allows the Angular origin:

```java
registry.addMapping("/api/**")
    .allowedOrigins("http://localhost:4200")
    .allowedMethods("GET", "POST", "OPTIONS")
    .allowedHeaders("*");
```

## ui — the front end

An Angular standalone-component page. `demo-api.service.ts` makes the call with
the dummy header; `app.component.ts` holds the `loggedIn` flag and renders the
result or an error. There's no real auth here on purpose — see the
[FAQ](../faq.md) for what Phase 2 adds.
