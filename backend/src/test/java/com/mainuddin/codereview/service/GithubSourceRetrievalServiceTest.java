package com.mainuddin.codereview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.dto.SatdCandidateDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.PullRequestFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GithubSourceRetrievalServiceTest {
    private static final String HEAD = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String BLOB = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String URL = "https://api.github.com/repos/owner/repo/contents/src/Main.java?ref=" + HEAD;
    private MockRestServiceServer server;
    private GithubSourceRetrievalService service;
    private final GithubProject project = GithubProject.builder()
            .githubRepoId(5L).ownerLogin("owner").name("repo").build();
    private final PullRequestFile file = PullRequestFile.builder().filename("src/Main.java").sha(BLOB).build();

    @BeforeEach
    void setup() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.createServer(rest);
        service = new GithubSourceRetrievalService(rest, new ObjectMapper());
    }

    private void content(String sha, String source, int size) {
        String encoded = Base64.getMimeEncoder(20, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(source.getBytes(StandardCharsets.UTF_8));
        String json = "{\"type\":\"file\",\"sha\":\"" + sha + "\",\"size\":" + size +
                ",\"encoding\":\"base64\",\"content\":\"" + encoded.replace("\n", "\\n") + "\"}";
        server.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void decodesVerifiedSourceAtExactHeadAndCachesPerSession() {
        String source = "class Main {\n  // TODO: refactor this\n}\n";
        content(BLOB, source, source.getBytes(StandardCharsets.UTF_8).length);
        var session = service.newSession();
        var first = session.retrieve(project, HEAD, file);
        assertEquals(VerifiedSourceResult.Status.VERIFIED, first.status());
        assertEquals(source, first.source());
        assertEquals(first, session.retrieve(project, HEAD, file));
        var candidate = SatdCandidateDTO.builder().filename("src/Main.java").lineNumber(2)
                .commentText("// TODO: refactor this").build();
        assertEquals(VerifiedSourceResult.Status.VERIFIED, session.forCandidate(project, HEAD, file, candidate).status());
        candidate.setLineNumber(3);
        assertEquals(VerifiedSourceResult.Status.LOCATION_MISMATCH,
                session.forCandidate(project, HEAD, file, candidate).status());
        server.verify();
    }

    @Test
    void rejectsBlobShaMismatch() {
        content(HEAD, "class Main {}", 13);
        assertEquals(VerifiedSourceResult.Status.SHA_MISMATCH, service.newSession().retrieve(project, HEAD, file).status());
        server.verify();
    }

    @Test
    void distinguishesMissingAndApiFailure() {
        server.expect(requestTo(URL)).andRespond(withResourceNotFound());
        assertEquals(VerifiedSourceResult.Status.UNAVAILABLE, service.newSession().retrieve(project, HEAD, file).status());
        server.verify();
    }

    @Test
    void serverFailureIsIsolatedAsApiFailure() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertEquals(VerifiedSourceResult.Status.API_FAILURE, service.newSession().retrieve(project, HEAD, file).status());
        server.verify();
    }

    @Test
    void rejectsOversizedOrUnsupportedContent() {
        content(BLOB, "small", GithubSourceRetrievalService.MAX_SOURCE_BYTES + 1);
        assertEquals(VerifiedSourceResult.Status.UNSUPPORTED, service.newSession().retrieve(project, HEAD, file).status());
        server.verify();
    }

    @Test
    void candidateVerificationDoesNotSearchNearbyLines() {
        String source = "// TODO: old\n// TODO: expected\n";
        var candidate = SatdCandidateDTO.builder().filename("src/Main.java").lineNumber(1)
                .commentText("// TODO: expected").build();
        assertFalse(GithubSourceRetrievalService.matchesCandidate(source, candidate));
        candidate.setLineNumber(2);
        assertTrue(GithubSourceRetrievalService.matchesCandidate(source, candidate));
    }

    @Test
    void invalidRevisionOrDeletedPathIsUnavailableWithoutDefaultBranchFallback() {
        assertEquals(VerifiedSourceResult.Status.UNAVAILABLE,
                service.newSession().retrieve(project, null, file).status());
        PullRequestFile deleted = PullRequestFile.builder().filename("src/Deleted.java").sha(BLOB).build();
        String deletedUrl = "https://api.github.com/repos/owner/repo/contents/src/Deleted.java?ref=" + HEAD;
        server.expect(requestTo(deletedUrl)).andRespond(withResourceNotFound());
        assertEquals(VerifiedSourceResult.Status.UNAVAILABLE,
                service.newSession().retrieve(project, HEAD, deleted).status());
        server.verify();
    }
}
