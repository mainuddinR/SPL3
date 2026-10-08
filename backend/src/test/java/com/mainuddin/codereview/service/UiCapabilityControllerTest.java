package com.mainuddin.codereview.service;

import com.mainuddin.codereview.security.JwtUtils;
import com.mainuddin.codereview.dto.GithubMonitorStatusDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ui_capability_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.llm.enabled=true", "app.llm.api-key=private-key-sentinel", "app.llm.model=demo-model" })
@AutoConfigureMockMvc
class UiCapabilityControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @Autowired FilterChainProxy securityFilterChain;
    @Autowired OAuth2AuthorizedClientService authorizedClientService;
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

    @Test
    void oauthCallbackStoresIntoTheSameAuthorizedClientServiceUsedByBackgroundWork() {
        var loginFilter = securityFilterChain.getFilters("/login/oauth2/code/github").stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast).findFirst().orElseThrow();
        Object repository = ReflectionTestUtils.getField(loginFilter, "authorizedClientRepository");
        assertInstanceOf(AuthenticatedPrincipalOAuth2AuthorizedClientRepository.class, repository);
        assertSame(authorizedClientService, ReflectionTestUtils.getField(repository, "authorizedClientService"));
    }

    @Test
    void monitorStatusIsAuthenticatedAndNeverReturnsOAuthCredentials() throws Exception {
        mvc.perform(get("/api/settings/github-monitor"))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()));
        when(statusService.githubMonitorStatus("123")).thenReturn(new GithubMonitorStatusDTO(true, false));
        String token = jwt.generateJwtToken("123", "test");
        var result = mvc.perform(get("/api/settings/github-monitor").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.githubSessionConnected").value(false))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("private-key-sentinel"));
        assertFalse(body.contains("accessToken"));
    }
}
