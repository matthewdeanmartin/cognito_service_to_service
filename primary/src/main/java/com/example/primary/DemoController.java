package com.example.primary;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private final SecondaryClient secondaryClient;

    public DemoController(SecondaryClient secondaryClient) {
        this.secondaryClient = secondaryClient;
    }

    @GetMapping("/api/demo")
    public Map<String, String> demo(
        @RequestHeader(name = "X-Demo-User", defaultValue = "anonymous") String demoUser
    ) {
        String label = secondaryClient.getLabel();

        return Map.of(
            "user", demoUser,
            "primary", "primary says hello",
            "secondaryLabel", label
        );
    }
}
