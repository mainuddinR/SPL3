package com.mainuddin.codereview.design;

import com.mainuddin.codereview.entity.GithubProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.security.GithubAuthorizedClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RepositorySourceSnapshotServiceTest {
    private static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BLOB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private final RestTemplate http = new RestTemplate();
    private final GithubAuthorizedClientService clients = mock(GithubAuthorizedClientService.class);
    private final GithubProject project = GithubProject.builder().ownerLogin("owner").name("repo").build();
    private MockRestServiceServer server;
    private RepositorySourceSnapshotService service;

    @BeforeEach void setup() {
        server = MockRestServiceServer.createServer(http);
        service = new RepositorySourceSnapshotService(http, clients, new ObjectMapper());
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "unit-test-token", Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600)));
        when(clients.loadValidForGithubId("123")).thenReturn(client);
    }

    @Test void obtainsStableCommitAndOnlyRelevantJavaBlobsWithoutCreatingTemporaryFiles() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo"))
                .andExpect(header("Authorization", "Bearer unit-test-token"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/commits/main"))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + SHA + "?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":false,\"tree\":["
                        + "{\"path\":\"src/App.java\",\"type\":\"blob\",\"sha\":\"" + BLOB + "\",\"size\":12},"
                        + "{\"path\":\"target/Generated.java\",\"type\":\"blob\",\"sha\":\"" + BLOB + "\",\"size\":12},"
                        + "{\"path\":\"README.md\",\"type\":\"blob\",\"sha\":\"" + BLOB + "\",\"size\":12}]}" , MediaType.APPLICATION_JSON));
        String code = "class App {}";
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/blobs/" + BLOB))
                .andRespond(withSuccess("{\"sha\":\"" + BLOB + "\",\"encoding\":\"base64\",\"size\":12,\"content\":\""
                        + Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8)) + "\"}", MediaType.APPLICATION_JSON));
        var head = service.currentHead(project, "123");
        var snapshot = service.capture(project, "123", head);
        assertEquals(SHA, snapshot.sha());
        assertEquals(code, snapshot.sources().get("src/App.java"));
        assertEquals(1, snapshot.sources().size());
        server.verify();
    }

    @Test void truncatedTreeIsRejectedInsteadOfPersistingAnIncompleteProfile() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + SHA + "?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":true,\"tree\":[]}", MediaType.APPLICATION_JSON));
        assertEquals("TREE_TRUNCATED", assertThrows(DesignSourceException.class, () -> service.capture(project, "123",
                new RepositorySourceSnapshotService.Head(SHA, "main"))).code());
        server.verify();
    }

    @Test void javaFileCountFailureRetainsObservedAndLimit() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "MAX_JAVA_FILES", 1);
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + SHA + "?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":false,\"tree\":[{\"path\":\"A.java\",\"type\":\"blob\",\"sha\":\"" + BLOB
                        + "\",\"size\":12},{\"path\":\"B.java\",\"type\":\"blob\",\"sha\":\"" + BLOB + "\",\"size\":12}]}", MediaType.APPLICATION_JSON));
        var error = assertThrows(DesignSourceException.class, () -> service.capture(project, "123", new RepositorySourceSnapshotService.Head(SHA,"main")));
        assertEquals("TOO_MANY_JAVA_FILES", error.code()); assertEquals(2L, error.observed()); assertEquals(1L, error.limit()); server.verify();
    }

    @Test void sourceFileAndTotalLimitsHaveDistinctCodes() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "MAX_SOURCE_BYTES", 10);
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + SHA + "?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":false,\"tree\":[{\"path\":\"A.java\",\"type\":\"blob\",\"sha\":\"" + BLOB + "\",\"size\":12}]}", MediaType.APPLICATION_JSON));
        assertEquals("JAVA_FILE_TOO_LARGE", assertThrows(DesignSourceException.class, () -> service.capture(project,"123",new RepositorySourceSnapshotService.Head(SHA,"main"))).code());
        server.verify(); server.reset();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "MAX_SOURCE_BYTES", 100);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "MAX_TOTAL_BYTES", 10);
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + SHA + "?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":false,\"tree\":[{\"path\":\"A.java\",\"type\":\"blob\",\"sha\":\"" + BLOB + "\",\"size\":12}]}", MediaType.APPLICATION_JSON));
        assertEquals("TOTAL_SOURCE_TOO_LARGE", assertThrows(DesignSourceException.class, () -> service.capture(project,"123",new RepositorySourceSnapshotService.Head(SHA,"main"))).code()); server.verify();
    }

    @Test void oversizedTreeResponseHasDistinctCode() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "MAX_TREE_RESPONSE_BYTES", 10);
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + SHA + "?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":false,\"tree\":[]}",MediaType.APPLICATION_JSON));
        assertEquals("TREE_RESPONSE_TOO_LARGE", assertThrows(DesignSourceException.class, () -> service.capture(project,"123",new RepositorySourceSnapshotService.Head(SHA,"main"))).code()); server.verify();
    }

    @Test void missingServerSideOAuthClientDoesNotFallBackToAnonymousGitHub() {
        when(clients.loadValidForGithubId("123")).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> service.currentHead(project, "123"));
        server.verify();
    }

    @Test void mismatchedBlobShaCannotEnterTheProfile() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + SHA + "?recursive=1"))
                .andRespond(withSuccess("{\"truncated\":false,\"tree\":[{\"path\":\"App.java\",\"type\":\"blob\",\"sha\":\""
                        + BLOB + "\",\"size\":12}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/blobs/" + BLOB))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\",\"encoding\":\"base64\",\"size\":12,\"content\":\"Y2xhc3MgQXBwIHt9\"}",
                        MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> service.capture(project, "123",
                new RepositorySourceSnapshotService.Head(SHA, "main")));
        server.verify();
    }
    @Test void loadsOnlyChangedBasePathsAtTheExactCommitAndSkipsAddedFiles() {
        var modified = new com.mainuddin.codereview.entity.PullRequestFile(); modified.setFilename("src/App.java"); modified.setStatus("modified");
        var added = new com.mainuddin.codereview.entity.PullRequestFile(); added.setFilename("src/New.java"); added.setStatus("added");
        server.expect(requestTo("https://api.github.com/repos/owner/repo/contents/src/App.java?ref=" + SHA))
                .andExpect(header("Authorization", "Bearer unit-test-token"))
                .andRespond(withSuccess("{\"type\":\"file\",\"sha\":\"" + BLOB + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/blobs/" + BLOB))
                .andRespond(withSuccess("{\"sha\":\"" + BLOB + "\",\"encoding\":\"base64\",\"size\":12,\"content\":\""
                        + Base64.getEncoder().encodeToString("class App {}".getBytes(StandardCharsets.UTF_8)) + "\"}", MediaType.APPLICATION_JSON));
        assertEquals(java.util.Map.of("src/App.java", "class App {}"), service.changedJavaBaseSources(project, "123", java.util.List.of(modified, added), SHA));
        server.verify();
    }
    @Test void rejectsAnUnknownBaseRevisionWithoutRequestingSource() {
        assertThrows(ResponseStatusException.class, () -> service.changedJavaBaseSources(project, "123", java.util.List.of(), "not-a-sha"));
        server.verify();
    }
    @Test void exactCommitUsesAuthenticatedImmutableShaWithoutDefaultBranchLookup() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/commits/" + SHA))
                .andExpect(header("Authorization", "Bearer unit-test-token"))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", MediaType.APPLICATION_JSON));
        var head = service.exactCommit(project, "123", SHA);
        assertEquals(SHA, head.sha()); assertNull(head.defaultBranch()); server.verify();
    }
    @Test void mismatchingCommitAndRateLimitNeverBecomeAnExactBaseline() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/commits/" + SHA))
                .andRespond(withSuccess("{\"sha\":\"" + BLOB + "\"}", MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> service.exactCommit(project, "123", SHA)); server.verify();
        server.reset();
        server.expect(requestTo("https://api.github.com/repos/owner/repo/commits/" + SHA))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("private provider details"));
        var failure = assertThrows(ResponseStatusException.class, () -> service.exactCommit(project, "123", SHA));
        assertEquals(429, failure.getStatusCode().value()); assertFalse(failure.getReason().contains("private")); server.verify();
    }
}
