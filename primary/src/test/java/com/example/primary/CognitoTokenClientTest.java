package com.example.primary;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Verifies the client fetches a token and then reuses the cached value
 * instead of hitting the endpoint again.
 */
class CognitoTokenClientTest {

    private MockWebServer server;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchesAndCachesToken() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"access_token\":\"tok-123\",\"expires_in\":3600}"));

        CognitoTokenProperties props = new CognitoTokenProperties(
            server.url("/oauth2/token").toString(),
            "client-id",
            "client-secret",
            "secondary/read");
        CognitoTokenClient client = new CognitoTokenClient(props, RestClient.builder().build());

        assertThat(client.getAccessToken()).isEqualTo("tok-123");
        // Second call is served from cache -> no second request.
        assertThat(client.getAccessToken()).isEqualTo("tok-123");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
