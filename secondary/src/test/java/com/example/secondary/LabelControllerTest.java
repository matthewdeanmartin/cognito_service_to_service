package com.example.secondary;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the acceptance criteria for {@code GET /api/label}:
 * no token -> 401, wrong scope -> 403, correct scope -> 200 + JSON.
 */
@WebMvcTest(LabelController.class)
@Import(SecurityConfig.class)
class LabelControllerTest {

    @Autowired
    private WebApplicationContext context;

    // The real decoder would fetch JWKS from Cognito at startup; mock it away.
    @MockBean
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/label"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenMissingScope_returns403() throws Exception {
        mockMvc.perform(get("/api/label")
                .with(jwt().authorities()))
            .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithReadScope_returnsLabel() throws Exception {
        mockMvc.perform(get("/api/label")
                .with(jwt().jwt(jwt -> jwt.claim("scope", "secondary/read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("secondary"))
            .andExpect(jsonPath("$.label").value("secondary says hello"));
    }
}
