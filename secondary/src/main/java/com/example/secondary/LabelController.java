package com.example.secondary;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LabelController {

    @GetMapping("/api/label")
    public Map<String, String> label() {
        return Map.of(
            "service", "secondary",
            "label", "secondary says hello"
        );
    }
}
