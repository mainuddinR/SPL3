package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.GithubProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GithubPullRequestClientTest {
    private final RestTemplate http = new RestTemplate();
    private final OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
    private final GithubProject project = GithubProject.builder().ownerLogin("owner").name("repo").build();
    private MockRestServiceServer server;
    private GithubPullRequestClient client;
    private static final String URL = "https://api.github.com/repos/owner/repo/pulls?state=all&per_page=100";

    @BeforeEach void setup() {
        server = MockRestServiceServer.createServer(http);
        client = new GithubPullRequestClient(http, new com.mainuddin.codereview.security.GithubAuthorizedClientService(clients));
        token(Instant.now().plusSeconds(3600));
    }

    private void token(Instant expiresAt) {
        OAuth2AuthorizedClient authorized = mock(OAuth2AuthorizedClient.class);
        when(authorized.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "dummy-test-token", Instant.now().minusSeconds(10), expiresAt));
        when(clients.loadAuthorizedClient("github", "user-id")).thenReturn(authorized);
    }

    @Test void fetchesPullRequestsWithOwnersOAuthToken() {
        server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer dummy-test-token"))
                .andRespond(withSuccess("[{\"id\":7,\"number\":3,\"title\":\"New PR\",\"state\":\"open\"}]",
                        MediaType.APPLICATION_JSON));
        assertTrue(client.hasValidSession("user-id"));
        assertEquals(7L, client.fetch("user-id", project).getFirst().getId());
        server.verify();
    }

    @Test void missingTokenNeverFallsBackToAnonymousAndProvider401RequiresReconnect() {
        when(clients.loadAuthorizedClient("github", "user-id")).thenReturn(null);
        assertFalse(client.hasValidSession("user-id"));
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(GithubDiscoveryException.class, () -> client.fetch("user-id", project)).status());

        token(Instant.now().minusSeconds(1));
        assertTrue(client.hasValidSession("user-id"));
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer dummy-test-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("provider rejected token"));
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(GithubDiscoveryException.class, () -> client.fetch("user-id", project)).status());
        server.verify();
    }

    @Test void rateLimitAndUnauthorizedFailuresHaveSafeMessages() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("private provider detail").header("X-RateLimit-Remaining", "0"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider detail"));
        var limited = assertThrows(GithubDiscoveryException.class, () -> client.fetch("user-id", project));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.status());
        assertFalse(limited.getMessage().contains("private provider detail"));
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(GithubDiscoveryException.class, () -> client.fetch("user-id", project)).status());
        server.verify();
    }
}
