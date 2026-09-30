package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.repository.*;
import com.mainuddin.codereview.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dashboard_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class DashboardServiceTest {
    @Autowired DashboardService dashboard;
    @Autowired UserRepository users;
    @Autowired GithubProjectRepository projects;
    @Autowired PullRequestRepository prs;
    @Autowired AnalysisRunRepository runs;
    @Autowired SATDFindingRepository findings;
    @Autowired JwtUtils jwt;
    @Autowired MockMvc mvc;
    @MockBean CodeBertClientService model;
    @MockBean DebtCategoryAssessmentService category;
    @MockBean SeverityAssessmentService severity;
    @MockBean JavaMethodMetricsService metrics;
    @MockBean GithubSourceRetrievalService sources;

    private String identity() { return UUID.randomUUID().toString(); }

    private GithubProject project(String githubId, String name) {
        User user = users.save(User.builder().githubId(githubId).username(name).build());
        return projects.save(GithubProject.builder().user(user).githubRepoId(System.nanoTime())
                .name(name).fullName(name + "/repo").ownerLogin(name)
                .htmlUrl("https://example.test/" + name).build());
    }

    private PullRequest pr(GithubProject project, int number) {
        return prs.save(PullRequest.builder().githubProject(project).githubPrId(System.nanoTime())
                .number(number).title("PR " + number).state("open").build());
    }

    private AnalysisRun run(PullRequest pr, AnalysisRunStatus status, LocalDateTime at, int candidates) {
        return runs.save(AnalysisRun.builder().pullRequest(pr).status(status).startedAt(at.minusMinutes(1))
                .completedAt(status == AnalysisRunStatus.COMPLETED ? at : null)
                .analyzedCandidateCount(status == AnalysisRunStatus.COMPLETED ? candidates : null).build());
    }

    private void finding(AnalysisRun run, String category, String categoryVersion, Integer score) {
        findings.save(SATDFinding.builder().analysisRun(run).filename("Main.java")
                .commentText("// saved finding").satdProbability(0.9).nonSatdProbability(0.1)
                .confidence(0.9).debtCategory(category).categoryRuleVersion(categoryVersion)
                .severityScore(score).severityRuleVersion(score == null ? null : "severity-rules-v1").build());
    }

    @Test
    void emptyOwnerHasZeroDashboardAndNoExternalCalls() throws Exception {
        String owner = identity();
        var data = dashboard.getDashboardData(owner);
        assertEquals(0, data.stats().repositoryCount());
        assertEquals(0, data.stats().pullRequestCount());
        assertEquals(0, data.stats().analyzedPullRequestCount());
        assertEquals(0, data.stats().currentSatdFindingCount());
        assertNull(data.stats().latestAnalysisAt());
        assertTrue(data.repositories().isEmpty());
        assertTrue(data.categoryDistribution().values().stream().allMatch(n -> n == 0));
        assertTrue(data.severityDistribution().values().stream().allMatch(n -> n == 0));
        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + jwt.generateJwtToken(owner, "owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stats.repositoryCount").value(0));
        verifyNoInteractions(model, category, severity, metrics, sources);
    }

    @Test
    void latestCompletedPerPrOwnsCurrentCountsAndSavedDistributions() throws Exception {
        String owner = identity();
        GithubProject first = project(owner, "first-" + UUID.randomUUID());
        // A second selected repository belongs to the same user, not a second account.
        GithubProject second = projects.save(GithubProject.builder().user(first.getUser())
                .githubRepoId(System.nanoTime()).name("second").fullName("owner/second")
                .ownerLogin("owner").htmlUrl("https://example.test/second").build());
        PullRequest repeated = pr(first, 1);
        PullRequest zeroSatd = pr(first, 2);
        pr(second, 3); // Never analyzed.
        PullRequest zeroCandidates = pr(second, 4);
        LocalDateTime time = LocalDateTime.of(2026, 9, 30, 11, 0);
        AnalysisRun old = run(repeated, AnalysisRunStatus.COMPLETED, time.minusDays(1), 3);
        finding(old, "DESIGN", "category-rules-v1", 5);
        finding(old, "DESIGN", "category-rules-v1", 5);
        AnalysisRun current = run(repeated, AnalysisRunStatus.COMPLETED, time, 8);
        List<String> categories = List.of("DESIGN", "DEFECT", "TEST", "REQUIREMENT", "DOCUMENTATION");
        for (int i = 0; i < categories.size(); i++) finding(current, categories.get(i), "category-rules-v1", i + 1);
        finding(current, null, "category-rules-v1", null); // Assessed but unclassified.
        finding(current, null, null, null); // Historical pre-category/pre-severity finding.
        run(repeated, AnalysisRunStatus.FAILED, time.plusDays(1), 0);
        run(repeated, AnalysisRunStatus.RUNNING, time.plusDays(2), 0);
        run(zeroSatd, AnalysisRunStatus.COMPLETED, time.minusHours(1), 2);
        run(zeroCandidates, AnalysisRunStatus.COMPLETED, time.minusHours(2), 0);

        var data = dashboard.getDashboardData(owner);
        assertEquals(2, data.stats().repositoryCount());
        assertEquals(4, data.stats().pullRequestCount());
        assertEquals(3, data.stats().analyzedPullRequestCount());
        assertEquals(7, data.stats().currentSatdFindingCount());
        assertEquals(time, data.stats().latestAnalysisAt());
        for (String key : categories) assertEquals(1, data.categoryDistribution().get(key));
        assertEquals(1, data.categoryDistribution().get("UNCLASSIFIED"));
        assertEquals(1, data.categoryDistribution().get("NOT_ASSESSED"));
        for (int score = 1; score <= 5; score++) assertEquals(1, data.severityDistribution().get(String.valueOf(score)));
        assertEquals(2, data.severityDistribution().get("NOT_ASSESSED"));
        assertEquals(2, data.repositories().get(0).pullRequestCount());
        assertEquals(2, data.repositories().get(0).analyzedPullRequestCount());
        assertEquals(7, data.repositories().get(0).currentSatdFindingCount());
        assertEquals(time, data.repositories().get(0).lastCompletedAnalysisAt());
        assertEquals(2, data.repositories().get(1).pullRequestCount());
        assertEquals(1, data.repositories().get(1).analyzedPullRequestCount());
        assertEquals(0, data.repositories().get(1).currentSatdFindingCount());
        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + jwt.generateJwtToken(owner, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.currentSatdFindingCount").value(7))
                .andExpect(jsonPath("$.categoryDistribution.UNCLASSIFIED").value(1))
                .andExpect(jsonPath("$.severityDistribution.NOT_ASSESSED").value(2));
        verifyNoInteractions(model, category, severity, metrics, sources);
    }

    @Test
    void equalCompletionTimeUsesHigherIdAndOtherOwnersNeverLeak() {
        String owner = identity();
        String stranger = identity();
        GithubProject owned = project(owner, "owned-" + UUID.randomUUID());
        GithubProject foreign = project(stranger, "foreign-" + UUID.randomUUID());
        PullRequest ownedPr = pr(owned, 10);
        PullRequest foreignPr = pr(foreign, 11);
        LocalDateTime time = LocalDateTime.of(2026, 9, 30, 12, 0);
        AnalysisRun olderId = run(ownedPr, AnalysisRunStatus.COMPLETED, time, 1);
        finding(olderId, "DESIGN", "category-rules-v1", 5);
        AnalysisRun newerId = run(ownedPr, AnalysisRunStatus.COMPLETED, time, 1);
        finding(newerId, "TEST", "category-rules-v1", 2);
        AnalysisRun foreignRun = run(foreignPr, AnalysisRunStatus.COMPLETED, time.plusHours(1), 1);
        finding(foreignRun, "DEFECT", "category-rules-v1", 4);

        var data = dashboard.getDashboardData(owner);
        assertEquals(1, data.stats().repositoryCount());
        assertEquals(1, data.stats().pullRequestCount());
        assertEquals(1, data.stats().analyzedPullRequestCount());
        assertEquals(1, data.stats().currentSatdFindingCount());
        assertEquals(time, data.stats().latestAnalysisAt());
        assertEquals(0, data.categoryDistribution().get("DESIGN"));
        assertEquals(1, data.categoryDistribution().get("TEST"));
        assertEquals(0, data.categoryDistribution().get("DEFECT"));
        assertEquals(0, data.severityDistribution().get("4"));
        assertEquals(1, data.severityDistribution().get("2"));
    }
}
