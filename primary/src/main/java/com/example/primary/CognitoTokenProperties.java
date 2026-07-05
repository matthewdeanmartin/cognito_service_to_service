package com.example.primary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cognito client-credentials settings, bound from {@code demo.cognito.*}.
 */
@ConfigurationProperties(prefix = "demo.cognito")
public record CognitoTokenProperties(
    String tokenUrl,
    String clientId,
    String clientSecret,
    String scope
) {
}
