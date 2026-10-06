package com.mainuddin.codereview.service;

import com.mainuddin.codereview.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ui_capability_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.llm.enabled=true", "app.llm.api-key=private-key-sentinel", "app.llm.model=demo-model" })
@AutoConfigureMockMvc
class UiCapabilityControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @MockBean UiStatusService statusService;

    @Test
    void capabilityIsAuthenticatedAndNeverReturnsTheProviderKey() throws Exception {
        mvc.perform(get("/api/settings/capabilities"))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()));
        String token = jwt.generateJwtToken("123", "test");
        var result = mvc.perform(get("/api/settings/capabilities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmEnabled").value(true))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.model").value("demo-model"))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("private-key-sentinel"));
    }
}
