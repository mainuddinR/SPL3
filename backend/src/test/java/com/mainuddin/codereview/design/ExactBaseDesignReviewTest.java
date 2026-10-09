package com.mainuddin.codereview.design;

import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.repository.*;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:exact_base_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class ExactBaseDesignReviewTest {
    private static final String BASE = "a".repeat(40), HEAD = "b".repeat(40);
    private static final AtomicLong IDS = new AtomicLong(88000);
    @Autowired UserRepository users;
    @Autowired GithubProjectRepository projects;
    @Autowired PullRequestRepository prs;
    @Autowired PullRequestFileRepository files;
    @Autowired RepositoryPatternProfileRepository profiles;
    @Autowired PrPatternCheckRepository checks;
    @Autowired RepositoryPatternProfileService service;
    @Autowired PatternConformanceService conformance;
    @Autowired DesignAdvicePersistence advicePersistence;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @MockBean RepositorySourceSnapshotService source;
    @MockBean DesignPatternDetectionService detection;

    private GithubProject project() {
        long id = IDS.incrementAndGet();
        var user = users.save(User.builder().githubId("exact-" + id).username("owner").role("ROLE_USER").build());
        return projects.save(GithubProject.builder().githubRepoId(id).name("repo").ownerLogin("owner")
                .fullName("owner/repo").htmlUrl("https://github.com/owner/repo").user(user).build());
    }
    private RepositoryPatternProfile saved(GithubProject p, String sha) {
        var profile = new RepositoryPatternProfile(); profile.setProject(p); profile.setStatus("COMPLETED");
        profile.setProgress("Completed"); profile.setDefaultBranch("main"); profile.setAnalyzedCommitSha(sha);
        profile.setJavaFileCount(1); profile.setAnalyzedAt(LocalDateTime.now()); return profiles.save(profile);
    }
    private void acquisition() {
        when(source.exactCommit(any(), anyString(), eq(BASE))).thenReturn(new RepositorySourceSnapshotService.Head(BASE, null));
        when(source.capture(any(), anyString(), any(), any())).thenReturn(new RepositorySourceSnapshotService.Snapshot(BASE, null,
                Map.of("Strategy.java", "interface Strategy {}")));
        when(detection.detect(any())).thenReturn(List.of(new PatternCandidate("STRATEGY", "GOF", "HIGH", "Baseline", "Structural evidence",
                List.of(new PatternCandidate.Evidence("Strategy", "Strategy", "Strategy.java", null, 1)))));
    }
    @Test void reusesOwnedCompletedExactProfileWithoutGitHubAndRejectsOtherOwner() {
        var p = project(); var prior = saved(p, BASE);
        assertEquals(prior.getId(), service.exactBase(p.getId(), p.getUser().getGithubId(), BASE).getId());
        assertThrows(ResourceNotFoundException.class, () -> service.exactBase(p.getId(), "other", BASE));
        verifyNoInteractions(source);
    }
    @Test void generatesExactProfileDeduplicatesRequestsAndPreservesDefaultHistory() throws Exception {
        var p = project(); var prior = saved(p, HEAD); acquisition();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(source.capture(any(), anyString(), any(), any())).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
            return new RepositorySourceSnapshotService.Snapshot(BASE, null, Map.of("Strategy.java", "interface Strategy {}"));
        });
        var first = service.exactBase(p.getId(), p.getUser().getGithubId(), BASE);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> service.exactBase(p.getId(), p.getUser().getGithubId(), BASE));
            assertEquals(first.getId(), second.get(5, TimeUnit.SECONDS).getId());
            assertEquals(prior.getId(), service.latest(p.getId(), p.getUser().getGithubId()).id());
        } finally { release.countDown(); }
        var completed = service.awaitExactBase(first.getId());
        assertEquals("COMPLETED", completed.getStatus()); assertEquals(BASE, completed.getAnalyzedCommitSha());
        assertNull(completed.getDefaultBranch());
        assertEquals(first.getId(), service.exactBase(p.getId(), p.getUser().getGithubId(), BASE).getId());
        assertEquals(prior.getId(), service.latest(p.getId(), p.getUser().getGithubId()).id());
        assertEquals(HEAD, profiles.findById(prior.getId()).orElseThrow().getAnalyzedCommitSha());
        verify(source, times(1)).capture(any(), anyString(), any(), any());
    }
    @Test void oldPrMismatchRecoversUsingNewExactProfileWithoutRewritingHistoricalCheck() throws Exception {
        var p = project(); var oldProfile = saved(p, HEAD); acquisition();
        var pr = prs.save(PullRequest.builder().githubPrId(IDS.incrementAndGet()).number(2).title("Extend strategy").state("open")
                .githubProject(p).baseSha(BASE).headSha(HEAD).syncedFilesBaseSha(BASE).syncedFilesHeadSha(HEAD).build());
        var file = new PullRequestFile(); file.setPullRequest(pr); file.setFilename("Push.java"); file.setStatus("added");
        file.setSha("c".repeat(40)); file.setPatch("+class Push implements Strategy {}"); files.save(file);
        var old = new PrPatternCheck(); old.setPullRequest(pr); old.setProfile(oldProfile); old.setStatus("COMPLETED");
        old.setProgress("Completed"); old.setCheckedAt(LocalDateTime.now()); old.setCheckedBaseSha(BASE); old.setCheckedHeadSha(HEAD);
        old.setFindingsJson("[]"); old = checks.save(old);
        assertEquals("INSUFFICIENT_EVIDENCE", conformance.latest(pr.getId(), p.getUser().getGithubId()).summary().overallStatus());
        when(source.changedJavaSources(any(), anyString(), any())).thenReturn(Map.of("Push.java", "class Push implements Strategy {}"));
        when(source.changedJavaBaseSources(any(), anyString(), any(), eq(BASE))).thenReturn(Map.of());
        conformance.start(pr.getId(), p.getUser().getGithubId());
        var result = finish(pr, p);
        assertEquals("COMPLETED", result.status()); assertEquals("CONFORMS", result.summary().overallStatus());
        assertNotEquals(oldProfile.getId(), result.profileId()); assertEquals(1, result.changedJavaFileCount());
        assertEquals("[]", checks.findById(old.getId()).orElseThrow().getFindingsJson());
        assertEquals(HEAD, profiles.findById(oldProfile.getId()).orElseThrow().getAnalyzedCommitSha());
        verify(source, never()).currentHead(any(), anyString());
    }
    @Test void failedExactSourceLeavesCheckFailedWithoutFalseConformance() throws Exception {
        var p = project(); acquisition();
        when(source.exactCommit(any(), anyString(), eq(BASE))).thenThrow(new DesignSourceException("SOURCE_PARSE_FAILED", "A Java source file could not be parsed safely.", null, null));
        var pr = prs.save(PullRequest.builder().githubPrId(IDS.incrementAndGet()).number(3).title("Fix").state("open")
                .githubProject(p).baseSha(BASE).headSha(HEAD).syncedFilesBaseSha(BASE).syncedFilesHeadSha(HEAD).build());
        conformance.start(pr.getId(), p.getUser().getGithubId()); var result = finish(pr, p);
        assertEquals("FAILED", result.status()); assertNull(result.summary()); assertTrue(result.findings().isEmpty());
        assertTrue(result.failureMessage().contains("parsed safely"));
        verify(source, never()).changedJavaSources(any(), anyString(), any());
    }
    @Test void adviceUsesExistingClobFirstWriteAndDoesNotChangeDeterministicHistory() throws Exception {
        var p = project(); var profile = saved(p, BASE);
        var pr = prs.save(PullRequest.builder().githubPrId(IDS.incrementAndGet()).number(4).title("Layer change").state("open").githubProject(p).build());
        var finding = new PatternConformanceDTO.Finding("CONTROLLER_SERVICE_REPOSITORY", "ARCHITECTURAL", "POTENTIAL_DEVIATION", "HIGH",
                "Controller.java", 5, "Controller", "Added repository dependency", "Use service",
                List.of(new PatternCandidate.Evidence("Repository", "Store", "Store.java", null, 1)), "Dependency", "Coupling", "HIGH", null);
        var check = new PrPatternCheck(); check.setPullRequest(pr); check.setProfile(profile); check.setStatus("COMPLETED");
        check.setProgress("Completed"); check.setCheckedAt(LocalDateTime.now()); check.setCheckedBaseSha(BASE); check.setCheckedHeadSha(HEAD);
        check.setFindingsJson(json.writeValueAsString(List.of(finding))); check = checks.save(check);
        var first = new PatternConformanceDTO.Advice("Summary", "Coupling", "Use service", "Test", "Partial context", LocalDateTime.now(), "provider", "design-advice-v1");
        var later = new PatternConformanceDTO.Advice("Other", "Risk", "Other fix", "Test", "Review", LocalDateTime.now(), "provider", "design-advice-v1");
        assertEquals(first, advicePersistence.saveOrGet(pr.getId(), check.getId(), 0, first));
        assertEquals(first, advicePersistence.saveOrGet(pr.getId(), check.getId(), 0, later));
        var saved = checks.findById(check.getId()).orElseThrow();
        var items = json.readValue(saved.getFindingsJson(), new com.fasterxml.jackson.core.type.TypeReference<List<PatternConformanceDTO.Finding>>() {});
        assertEquals(finding, items.getFirst().withAdvice(null)); assertEquals(first, items.getFirst().aiSuggestion());
        assertEquals(BASE, saved.getCheckedBaseSha()); assertEquals(HEAD, saved.getCheckedHeadSha()); assertEquals("COMPLETED", saved.getStatus());
    }
    private PatternConformanceDTO finish(PullRequest pr, GithubProject p) throws Exception {
        for (int i = 0; i < 250; i++) {
            var result = conformance.latest(pr.getId(), p.getUser().getGithubId());
            if (!"RUNNING".equals(result.status())) return result;
            Thread.sleep(20);
        }
        throw new AssertionError("Check did not finish");
    }
}
