package com.mainuddin.codereview.service;

import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import javax.sql.DataSource;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UiGithubMonitorStatusTest {
    private final OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
    private UiStatusService service(boolean enabled) {
        return new UiStatusService(mock(UserRepository.class), mock(GithubProjectRepository.class),
                new com.mainuddin.codereview.security.GithubAuthorizedClientService(clients), mock(DataSource.class), "http://localhost:8001", false, "", "test", enabled);
    }

    @Test void missingSessionRequiresReconnectWhileStoredTokenRemainsConnectedUntilProviderRejectsIt() {
        assertFalse(service(true).githubMonitorStatus("owner").githubSessionConnected());
        OAuth2AuthorizedClient authorized = mock(OAuth2AuthorizedClient.class);
        when(authorized.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "private-token", Instant.now().minusSeconds(30), Instant.now().minusSeconds(1)));
        when(clients.loadAuthorizedClient("github", "owner")).thenReturn(authorized);

        // Expiry metadata alone is not authoritative for GitHub. The downstream request path
        // handles a real provider HTTP 401 by requiring reconnect.
        assertTrue(service(true).githubMonitorStatus("owner").githubSessionConnected());
    }

    @Test void activeOAuthSessionReportsAvailableWithoutReturningToken() {
        OAuth2AuthorizedClient authorized = mock(OAuth2AuthorizedClient.class);
        when(authorized.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "private-token", Instant.now().minusSeconds(30), Instant.now().plusSeconds(3600)));
        when(clients.loadAuthorizedClient("github", "owner")).thenReturn(authorized);
        var status = service(true).githubMonitorStatus("owner");
        assertTrue(status.enabled());
        assertTrue(status.githubSessionConnected());
        assertFalse(status.toString().contains("private-token"));
        assertFalse(service(false).githubMonitorStatus("owner").enabled());
    }
}
