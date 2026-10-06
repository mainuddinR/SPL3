package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GithubDiscoveryServiceTest {
    private final RestTemplate http = new RestTemplate();
    private final OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
    private final GithubProjectRepository projects = mock(GithubProjectRepository.class);
    private final GithubService github = mock(GithubService.class);
    private MockRestServiceServer server;
    private GithubDiscoveryService service;

    @BeforeEach
    void setup() {
        server = MockRestServiceServer.createServer(http);
        service = new GithubDiscoveryService(http, clients, projects, github);
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "dummy-test-token", java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600)));
        when(clients.loadAuthorizedClient("github", "user-id")).thenReturn(client);
        when(projects.findByUserGithubIdOrderByIdAsc("user-id")).thenReturn(List.of());
    }

    @Test
    void mineUsesSignedInTokenAndPreservesPagination() {
        server.expect(once(), requestTo("https://api.github.com/user/repos?per_page=50&page=2&sort=updated"))
                .andExpect(header("Authorization", "Bearer dummy-test-token"))
                .andRespond(withSuccess("[{\"id\":7,\"name\":\"portfolio\",\"owner\":{\"login\":\"me\"}}]", MediaType.APPLICATION_JSON)
                        .header("Link", "<https://api.github.com/user/repos?page=3>; rel=\"next\""));
        var page = service.mine("user-id", 2);
        assertEquals(2, page.page());
        assertTrue(page.hasNext());
        assertEquals("portfolio", page.repositories().getFirst().getName());
        server.verify();
    }

    @Test
    void missingTokenNeverFallsBackToAnonymous() {
        when(clients.loadAuthorizedClient("github", "user-id")).thenReturn(null);
        GithubDiscoveryException ex = assertThrows(GithubDiscoveryException.class, () -> service.mine("user-id", 1));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
        assertFalse(ex.getMessage().contains("dummy-test-token"));
        server.verify();
    }

    @Test
    void exactUserIsResolvedThenItsPublicRepositoriesLoaded() {
        server.expect(requestTo("https://api.github.com/users/mainuddinR"))
                .andExpect(header("Authorization", "Bearer dummy-test-token"))
                .andRespond(withSuccess("{\"login\":\"mainuddinR\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/users/mainuddinR/repos?per_page=50&page=1&sort=updated&type=owner"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(service.byUser("user-id", "mainuddinR", 1).repositories().isEmpty());
        server.verify();
    }

    @Test
    void exactRepositoryAndStoreUseProviderMetadata() {
        String json = "{\"id\":7,\"name\":\"canonical\",\"full_name\":\"owner/canonical\",\"owner\":{\"login\":\"owner\"}}";
        server.expect(requestTo("https://api.github.com/repos/owner/canonical"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/canonical"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertEquals(7L, service.exact("user-id", "owner", "canonical").getId());
        GithubProject saved = new GithubProject();
        when(github.saveSelectedRepository(any(), eq("user-id"))).thenReturn(saved);
        assertSame(saved, service.store("user-id", "owner", "canonical"));
        verify(github).saveSelectedRepository(argThat(repo -> repo.getId() == 7L), eq("user-id"));
        server.verify();
    }

    @Test
    void rateLimitIs429WithoutProviderBodyOrToken() {
        server.expect(requestTo("https://api.github.com/user/repos?per_page=50&page=1&sort=updated"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"message\":\"API rate limit exceeded for secret IP\"}")
                        .header("X-RateLimit-Remaining", "0"));
        GithubDiscoveryException ex = assertThrows(GithubDiscoveryException.class, () -> service.mine("user-id", 1));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status());
        assertFalse(ex.getMessage().contains("secret IP"));
    }

    @Test
    void invalidSessionAndMissingUserHaveSafeStatuses() {
        server.expect(requestTo("https://api.github.com/user/repos?per_page=50&page=1&sort=updated"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider detail"));
        server.expect(requestTo("https://api.github.com/users/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("private provider detail"));
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(GithubDiscoveryException.class, () -> service.mine("user-id", 1)).status());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(GithubDiscoveryException.class, () -> service.byUser("user-id", "missing", 1)).status());
    }
}
