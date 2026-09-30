package com.mainuddin.codereview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.dto.GithubFileDTO;
import com.mainuddin.codereview.dto.GithubPullRequestDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import com.mainuddin.codereview.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GithubRevisionSyncTest {
    private static final String PULL_URL = "https://api.github.com/repos/owner/repo/pulls/7";
    private final RestTemplate restTemplate = new RestTemplate();
    private MockRestServiceServer server;
    private PullRequestFileSnapshotService fileSnapshots;
    private PullRequestRepository prs;
    private GithubProjectRepository projects;
    private GithubService service;

    @BeforeEach
    void setup() {
        server = MockRestServiceServer.createServer(restTemplate);
        prs = mock(PullRequestRepository.class);
        projects = mock(GithubProjectRepository.class);
        fileSnapshots = mock(PullRequestFileSnapshotService.class);
        GithubProject project = GithubProject.builder().githubRepoId(5L).ownerLogin("owner").name("repo").build();
        PullRequest pr = PullRequest.builder().id(19L).number(7).githubProject(project).build();
        when(prs.findWithProjectById(19L)).thenReturn(java.util.Optional.of(pr));
        when(projects.findById(5L)).thenReturn(java.util.Optional.of(project));
        service = new GithubService(restTemplate, projects, mock(UserRepository.class),
                prs, fileSnapshots, mock(PullRequestCommentSnapshotService.class));
    }

    private void revision(String head, String base) {
        server.expect(once(), requestTo(PULL_URL)).andRespond(withSuccess(
                "{\"head\":{\"sha\":\"" + head + "\"},\"base\":{\"sha\":\"" + base + "\"}}", MediaType.APPLICATION_JSON));
    }

    private void files(int page, int count) {
        String body = "[" + String.join(",", java.util.Collections.nCopies(count,
                "{\"sha\":\"blob-sha\",\"filename\":\"Main.java\",\"patch\":\"@@ -1 +1 @@\"}")) + "]";
        server.expect(once(), requestTo(PULL_URL + "/files?per_page=100&page=" + page))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void comments() {
        server.expect(once(), requestTo(PULL_URL + "/comments"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    }

    @Test
    void mapsGitHubHeadAndBaseSeparately() throws Exception {
        var dto = new ObjectMapper().readValue("{\"head\":{\"sha\":\"commit-head\"},\"base\":{\"sha\":\"commit-base\"}}",
                GithubPullRequestDTO.class);
        assertEquals("commit-head", dto.getHead().getSha());
        assertEquals("commit-base", dto.getBase().getSha());
    }

    @Test
    void prListSyncStoresCurrentHeadAndBaseWithoutChangingFileSnapshot() {
        String url = "https://api.github.com/repos/owner/repo/pulls?state=all&per_page=100";
        server.expect(requestTo(url)).andRespond(withSuccess(
                "[{\"id\":77,\"number\":7,\"title\":\"PR\",\"state\":\"open\"," +
                "\"head\":{\"sha\":\"new-head\"},\"base\":{\"sha\":\"new-base\"}}]", MediaType.APPLICATION_JSON));
        PullRequest stored = PullRequest.builder().syncedFilesHeadSha("old-files-head")
                .syncedFilesBaseSha("old-files-base").build();
        when(prs.findByGithubPrId(77L)).thenReturn(java.util.Optional.of(stored));
        service.fetchAndStorePullRequests(5L);
        assertEquals("new-head", stored.getHeadSha());
        assertEquals("new-base", stored.getBaseSha());
        assertEquals("old-files-head", stored.getSyncedFilesHeadSha());
        assertEquals("old-files-base", stored.getSyncedFilesBaseSha());
        verify(prs).save(stored);
        server.verify();
    }

    @Test
    void onePageSyncPersistsTheConsistentRevisionAndBlobSha() {
        revision("head-a", "base-a");
        files(1, 1);
        revision("head-a", "base-a");
        comments();
        service.fetchAndStorePullRequestDetails(19L);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<GithubFileDTO>> captured = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(fileSnapshots).replace(eq(19L), eq("head-a"), eq("base-a"), captured.capture());
        assertEquals(1, captured.getValue().size());
        assertEquals("blob-sha", captured.getValue().get(0).getSha());
        assertNotEquals("head-a", captured.getValue().get(0).getSha());
        server.verify();
    }

    @Test
    void continuesAcrossFullPageAndStopsOnShortFinalPage() {
        revision("head-a", "base-a");
        files(1, 100);
        files(2, 2);
        revision("head-a", "base-a");
        comments();
        service.fetchAndStorePullRequestDetails(19L);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<GithubFileDTO>> captured = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(fileSnapshots).replace(eq(19L), eq("head-a"), eq("base-a"), captured.capture());
        assertEquals(102, captured.getValue().size());
        server.verify();
    }

    @Test
    void laterPageFailureDoesNotReplaceStoredFiles() {
        revision("head-a", "base-a");
        files(1, 100);
        server.expect(requestTo(PULL_URL + "/files?per_page=100&page=2")).andRespond(withServerError());
        assertThrows(Exception.class, () -> service.fetchAndStorePullRequestDetails(19L));
        verifyNoInteractions(fileSnapshots);
        server.verify();
    }

    @Test
    void headOrBaseChangeDoesNotReplaceStoredFiles() {
        revision("head-a", "base-a");
        files(1, 1);
        revision("head-b", "base-a");
        assertThrows(IllegalStateException.class, () -> service.fetchAndStorePullRequestDetails(19L));
        verifyNoInteractions(fileSnapshots);
        server.verify();
    }

    @Test
    void baseChangeDoesNotReplaceStoredFiles() {
        revision("head-a", "base-a");
        files(1, 1);
        revision("head-a", "base-b");
        assertThrows(IllegalStateException.class, () -> service.fetchAndStorePullRequestDetails(19L));
        verifyNoInteractions(fileSnapshots);
        server.verify();
    }

    @Test
    void ceilingIsReportedInsteadOfClaimingCompleteness() {
        revision("head-a", "base-a");
        for (int page = 1; page <= 30; page++) files(page, 100);
        assertThrows(IllegalStateException.class, () -> service.fetchAndStorePullRequestDetails(19L));
        verifyNoInteractions(fileSnapshots);
        server.verify();
    }
}
