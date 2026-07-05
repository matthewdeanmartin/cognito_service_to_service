package com.example.primary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({DemoProperties.class, CognitoTokenProperties.class})
public class PrimaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrimaryApplication.class, args);
    }
}
