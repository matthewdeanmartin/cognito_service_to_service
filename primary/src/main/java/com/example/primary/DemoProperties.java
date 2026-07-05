package com.example.primary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Non-Cognito settings for the primary service.
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(String secondaryBaseUrl) {
}
