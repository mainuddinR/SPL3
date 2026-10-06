package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:review_publish_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class ReviewPublishingServiceTest {
    @Autowired ReviewPublishingService service;
    @Autowired UserRepository users;
    @Autowired GithubProjectRepository projects;
    @Autowired PullRequestRepository prs;
    @Autowired PullRequestFileRepository files;
    @Autowired AnalysisRunRepository runs;
    @Autowired SATDFindingRepository findings;
    @MockBean GitHubReviewClient github;
    private PullRequest pr;
    private AnalysisRun run;
    private String ownerId;

    @BeforeEach
    void setup() {
        ownerId = UUID.randomUUID().toString();
        User user = users.save(User.builder().githubId(ownerId).username("owner").build());
        GithubProject project = projects.save(GithubProject.builder().user(user).githubRepoId(System.nanoTime())
                .name("repo").fullName("owner/repo").ownerLogin("owner").htmlUrl("https://github.com/owner/repo").build());
        pr = prs.save(PullRequest.builder().githubProject(project).githubPrId(System.nanoTime()).number(19)
                .title("PR").state("open").headSha("abc").syncedFilesHeadSha("abc").build());
        run = runs.save(AnalysisRun.builder().pullRequest(pr).status(AnalysisRunStatus.COMPLETED)
                .analyzedHeadSha("abc").analyzedCandidateCount(20).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ownerId, null, List.of()));
        when(github.currentHead(ownerId, "owner", "repo", 19)).thenReturn("abc");
    }

    @AfterEach
    void teardown() { SecurityContextHolder.clearContext(); reset(github); }

    @Test
    void zeroSatdPublishesSummaryOnceAndPersistsMetadata() {
        when(github.publish(eq(ownerId), eq("owner"), eq("repo"), eq(19), eq("abc"), anyString(), anyList()))
                .thenReturn(new GitHubReviewClient.PublishedReview(123L, "https://github.com/owner/repo/pull/19#pullrequestreview-123"));
        var preview = service.preview(pr.getId(), run.getId());
        assertEquals(0, preview.satdFindingCount());
        assertEquals(0, preview.inlineCommentCount());
        assertTrue(preview.summary().contains("Candidate comments analyzed: 20"));
        var published = service.publish(pr.getId(), run.getId(), true);
        assertEquals(ReviewPublicationStatus.PUBLISHED, published.publicationStatus());
        assertEquals(123L, runs.findById(run.getId()).orElseThrow().getGithubReviewId());
        service.publish(pr.getId(), run.getId(), true);
        verify(github, times(1)).publish(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyList());
    }

    @Test
    void mapsOnlyExactRightSideLinesAndFallsBackForOtherFindings() {
        files.save(PullRequestFile.builder().pullRequest(pr).filename("src/X.java")
                .patch("@@ -3,2 +3,3 @@\n old\n+// TODO\n unchanged").build());
        findings.save(finding("src/X.java", 4));
        findings.save(finding("src/X.java", 90));
        var preview = service.preview(pr.getId(), run.getId());
        assertEquals(1, preview.inlineCommentCount());
        assertEquals(1, preview.unmappableCount());
        assertTrue(preview.summary().contains("src/X.java:90"));
        assertEquals(4, preview.inlineComments().getFirst().line());
    }

    @Test
    void rejectsStaleRevisionWithoutPublishing() {
        when(github.currentHead(ownerId, "owner", "repo", 19)).thenReturn("new-head");
        assertTrue(service.preview(pr.getId(), run.getId()).staleRevision());
        assertThrows(ReviewPublishException.class, () -> service.publish(pr.getId(), run.getId(), true));
        verify(github, never()).publish(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyList());
    }

    @Test
    void ownershipAndRunStatusAreEnforced() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone-else", null, List.of()));
        assertThrows(RuntimeException.class, () -> service.preview(pr.getId(), run.getId()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ownerId, null, List.of()));
        run.setStatus(AnalysisRunStatus.RUNNING);
        runs.save(run);
        assertThrows(ReviewPublishException.class, () -> service.preview(pr.getId(), run.getId()));
        run.setStatus(AnalysisRunStatus.FAILED);
        runs.save(run);
        assertThrows(ReviewPublishException.class, () -> service.publish(pr.getId(), run.getId(), true));
    }

    @Test
    void providerFailureKeepsAnalysisAndPreventsBlindRetry() {
        when(github.publish(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyList()))
                .thenThrow(new ReviewPublishException(org.springframework.http.HttpStatus.FORBIDDEN, "GitHub permission denied."));
        assertThrows(ReviewPublishException.class, () -> service.publish(pr.getId(), run.getId(), true));
        AnalysisRun saved = runs.findById(run.getId()).orElseThrow();
        assertEquals(AnalysisRunStatus.COMPLETED, saved.getStatus());
        assertEquals(ReviewPublicationStatus.PUBLISH_FAILED, saved.getPublicationStatus());
        service.publish(pr.getId(), run.getId(), true);
        verify(github, times(1)).publish(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyList());
    }

    @Test
    void savedSuggestionIsIncludedWithoutGeneratingNewOne() {
        SATDFinding saved = finding("src/X.java", 1);
        saved.setAiSuggestion("Summary\nRefactor the shared path.\n\nRisk\nUnexpected behavior.");
        findings.save(saved);
        files.save(PullRequestFile.builder().pullRequest(pr).filename("src/X.java")
                .patch("@@ -1 +1 @@\n+// TODO debt").build());
        var preview = service.preview(pr.getId(), run.getId());
        assertEquals(1, preview.satdFindingCount());
        assertTrue(preview.inlineComments().getFirst().body().contains("Refactor the shared path"));
        assertFalse(preview.inlineComments().getFirst().body().contains("Unexpected behavior"));
    }

    @Test
    void runFromAnotherPullRequestIsNotAccessible() {
        PullRequest other = prs.save(PullRequest.builder().githubProject(pr.getGithubProject())
                .githubPrId(System.nanoTime()).number(20).title("Other").state("open").build());
        assertThrows(RuntimeException.class, () -> service.preview(other.getId(), run.getId()));
        assertThrows(RuntimeException.class, () -> service.publish(other.getId(), run.getId(), true));
        verify(github, never()).publish(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyList());
    }

    private SATDFinding finding(String file, int line) {
        return SATDFinding.builder().analysisRun(run).filename(file).lineNumber(line)
                .commentText("// TODO debt").satdProbability(0.9).nonSatdProbability(0.1)
                .confidence(0.9).debtCategory("DESIGN").severityScore(3).build();
    }
}
