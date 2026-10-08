package com.mainuddin.codereview.design;

import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.User;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.PatternOccurrenceRepository;
import com.mainuddin.codereview.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pattern_profile_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class RepositoryPatternProfilePersistenceTest {
    @Autowired UserRepository users;
    @Autowired GithubProjectRepository projects;
    @Autowired PatternOccurrenceRepository occurrences;
    @Autowired RepositoryPatternProfileService service;
    @MockBean RepositorySourceSnapshotService snapshots;
    @MockBean DesignPatternDetectionService detection;

    @Test void savesCommitBoundProfileAndEnforcesRepositoryOwner() throws Exception {
        String sha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        User user = users.save(User.builder().githubId("pattern-owner").username("owner").role("ROLE_USER").build());
        GithubProject project = projects.save(GithubProject.builder().githubRepoId(987654321L)
                .name("fixture").fullName("owner/fixture").htmlUrl("https://github.com/owner/fixture")
                .ownerLogin("owner").user(user).build());
        when(snapshots.currentHead(any(), eq("pattern-owner")))
                .thenReturn(new RepositorySourceSnapshotService.Head(sha, "main"));
        when(snapshots.capture(any(), eq("pattern-owner"), any(), any()))
                .thenReturn(new RepositorySourceSnapshotService.Snapshot(sha, "main",
                        Map.of("src/Strategy.java", "interface Strategy {}")));
        when(detection.detect(any())).thenReturn(List.of(new PatternCandidate("STRATEGY", "GOF", "HIGH",
                "Structural fixture", "Four structural signals", List.of(
                new PatternCandidate.Evidence("Strategy", "Strategy", "src/Strategy.java", null, 1)))));

        assertThrows(ResourceNotFoundException.class, () -> service.start(project.getId(), "other-user"));
        service.start(project.getId(), "pattern-owner");
        PatternProfileDTO result = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            result = service.latest(project.getId(), "pattern-owner");
            if ("COMPLETED".equals(result.status())) break;
            Thread.sleep(20);
        }
        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertEquals(sha, result.analyzedCommitSha());
        assertEquals(1, result.javaFileCount());
        assertEquals(1, result.occurrences().size());
        assertEquals("LOCALIZED", result.designSummary().primaryPatternStatus());
        assertEquals(1, result.designSummary().patternSummaries().getFirst().occurrenceCount());
        assertEquals("HIGH", result.occurrences().getFirst().confidence());
        assertEquals(1, occurrences.findByProfileIdOrderByIdAsc(result.id()).size());
        assertThrows(ResourceNotFoundException.class, () -> service.latest(project.getId(), "other-user"));
    }
}
