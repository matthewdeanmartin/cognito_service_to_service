package com.example.primary;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Obtains a Cognito access token via the OAuth 2.0 client-credentials grant and
 * caches it in memory, reusing it until 60 seconds before expiry.
 */
@Component
public class CognitoTokenClient {

    private final CognitoTokenProperties properties;
    private final RestClient restClient;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public CognitoTokenClient(CognitoTokenProperties properties) {
        this(properties, RestClient.builder().build());
    }

    // Visible for testing: inject a RestClient bound to a stub token endpoint.
    CognitoTokenClient(CognitoTokenProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public String getAccessToken() {
        CachedToken existing = cachedToken.get();
        if (existing != null && existing.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return existing.accessToken();
        }

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", properties.scope());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
            .uri(properties.tokenUrl())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Cognito token response did not include access_token");
        }

        String accessToken = response.get("access_token").toString();
        Number expiresIn = (Number) response.getOrDefault("expires_in", 3600);
        Instant expiresAt = Instant.now().plusSeconds(expiresIn.longValue());

        cachedToken.set(new CachedToken(accessToken, expiresAt));
        return accessToken;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
