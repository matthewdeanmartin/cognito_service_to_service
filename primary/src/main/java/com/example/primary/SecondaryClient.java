package com.example.primary;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SecondaryClient {

    private final RestClient restClient;
    private final CognitoTokenClient tokenClient;

    public SecondaryClient(DemoProperties properties, CognitoTokenClient tokenClient) {
        this.tokenClient = tokenClient;
        this.restClient = RestClient.builder()
            .baseUrl(properties.secondaryBaseUrl())
            .build();
    }

    public String getLabel() {
        String token = tokenClient.getAccessToken();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.get()
            .uri("/api/label")
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .body(Map.class);

        if (response == null || response.get("label") == null) {
            throw new IllegalStateException("Secondary response did not include label");
        }

        return response.get("label").toString();
    }
}
