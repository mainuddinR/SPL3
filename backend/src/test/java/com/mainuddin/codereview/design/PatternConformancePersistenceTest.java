package com.mainuddin.codereview.design;

import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pattern_check_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class PatternConformancePersistenceTest {
    private static final String BASE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HEAD = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    @Autowired UserRepository users;
    @Autowired GithubProjectRepository projects;
    @Autowired PullRequestRepository prs;
    @Autowired PullRequestFileRepository files;
    @Autowired RepositoryPatternProfileRepository profiles;
    @Autowired PatternOccurrenceRepository occurrences;
    @Autowired PrPatternCheckRepository checks;
    @Autowired PatternConformanceService service;
    @MockBean RepositorySourceSnapshotService snapshots;

    @Test void storesStableCheckAtProfileAndPrRevisionWithOwnerProtection() throws Exception {
        User owner = users.save(User.builder().githubId("check-owner").username("owner").role("ROLE_USER").build());
        GithubProject project = projects.save(GithubProject.builder().githubRepoId(999888L).name("fixture")
                .fullName("owner/fixture").htmlUrl("https://github.com/owner/fixture")
                .ownerLogin("owner").user(owner).build());
        PullRequest pr = prs.save(PullRequest.builder().githubPrId(991L).number(9).title("Add push strategy")
                .state("open").githubProject(project).headSha(HEAD).baseSha(BASE)
                .syncedFilesHeadSha(HEAD).syncedFilesBaseSha(BASE).build());
        PullRequestFile file = new PullRequestFile();
        file.setPullRequest(pr); file.setFilename("PushNotificationStrategy.java");
        file.setStatus("added"); file.setSha("cccccccccccccccccccccccccccccccccccccccc");
        file.setPatch("+class PushNotificationStrategy implements NotificationStrategy {}");
        files.save(file);
        RepositoryPatternProfile profile = new RepositoryPatternProfile();
        profile.setProject(project); profile.setStatus("COMPLETED"); profile.setProgress("Completed");
        profile.setAnalyzedCommitSha(BASE); profile.setDefaultBranch("main");
        profile.setAnalyzedAt(LocalDateTime.now()); profile.setJavaFileCount(4);
        profile = profiles.save(profile);
        PatternOccurrence occurrence = new PatternOccurrence();
        occurrence.setProfile(profile); occurrence.setPatternType("STRATEGY"); occurrence.setPatternFamily("GOF");
        occurrence.setConfidence("HIGH"); occurrence.setRationale("Baseline"); occurrence.setConfidenceReason("Four signals");
        occurrence.setEvidenceJson("[{\"role\":\"Strategy\",\"className\":\"NotificationStrategy\",\"sourcePath\":\"NotificationStrategy.java\",\"methodName\":null,\"line\":1}]");
        occurrences.save(occurrence);
        when(snapshots.changedJavaSources(any(), eq("check-owner"), any()))
                .thenReturn(Map.of("PushNotificationStrategy.java",
                        "class PushNotificationStrategy implements NotificationStrategy { public void send() {} }"));

        assertThrows(ResourceNotFoundException.class, () -> service.start(pr.getId(), "other-user"));
        service.start(pr.getId(), "check-owner");
        PatternConformanceDTO result = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            result = service.latest(pr.getId(), "check-owner");
            if ("COMPLETED".equals(result.status())) break;
            Thread.sleep(20);
        }
        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertEquals(profile.getId(), result.profileId());
        assertEquals(BASE, result.checkedBaseSha());
        assertEquals(HEAD, result.checkedHeadSha());
        assertEquals("CONFORMS", result.findings().getFirst().status());
        assertEquals(1, checks.count());
        service.latest(pr.getId(), "check-owner");
        verify(snapshots, times(1)).changedJavaSources(any(), eq("check-owner"), any());
        assertThrows(ResourceNotFoundException.class, () -> service.latest(pr.getId(), "other-user"));
        Long originalCheckId = result.id();
        service.start(pr.getId(), "check-owner");
        PatternConformanceDTO rerun = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            rerun = service.latest(pr.getId(), "check-owner");
            if ("COMPLETED".equals(rerun.status())) break;
            Thread.sleep(20);
        }
        assertNotEquals(originalCheckId, rerun.id());
        assertEquals(profile.getId(), rerun.profileId());
        assertEquals(BASE, rerun.checkedBaseSha()); assertEquals(HEAD, rerun.checkedHeadSha());
        var original = checks.findById(originalCheckId).orElseThrow();
        assertEquals(BASE, original.getCheckedBaseSha()); assertEquals(HEAD, original.getCheckedHeadSha());
        assertEquals(2, checks.count());
        // Even a historical check with an invalidated profile revision cannot advertise conformance.
        profile.setAnalyzedCommitSha(HEAD); profiles.save(profile);
        assertEquals("INSUFFICIENT_EVIDENCE", service.latest(pr.getId(), "check-owner").summary().overallStatus());
        verify(snapshots, times(2)).changedJavaSources(any(), eq("check-owner"), any());
    }
}
