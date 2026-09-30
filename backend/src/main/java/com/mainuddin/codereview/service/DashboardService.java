package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.DashboardResponseDTO;
import com.mainuddin.codereview.dto.DashboardStatsDTO;
import com.mainuddin.codereview.dto.RepositorySummaryDTO;
import com.mainuddin.codereview.entity.AnalysisRunStatus;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.repository.AnalysisRunRepository;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import com.mainuddin.codereview.repository.SATDFindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private final GithubProjectRepository projectRepository;
    private final PullRequestRepository pullRequestRepository;
    private final AnalysisRunRepository runRepository;
    private final SATDFindingRepository findingRepository;

    public DashboardService(GithubProjectRepository projectRepository, PullRequestRepository pullRequestRepository,
                            AnalysisRunRepository runRepository, SATDFindingRepository findingRepository) {
        this.projectRepository = projectRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
    }

    private static class RepositoryCounts {
        final GithubProject project;
        long pullRequests;
        long analyzedPullRequests;
        long findings;
        LocalDateTime lastCompleted;

        RepositoryCounts(GithubProject project) { this.project = project; }

        RepositorySummaryDTO toDto() {
            return new RepositorySummaryDTO(project.getId(), project.getName(), project.getHtmlUrl(),
                    pullRequests, analyzedPullRequests, findings, lastCompleted);
        }
    }

    @Transactional(readOnly = true)
    public DashboardResponseDTO getDashboardData(String githubId) {
        List<GithubProject> projects = projectRepository.findByUserGithubIdOrderByIdAsc(githubId);
        Map<Long, RepositoryCounts> byProject = new LinkedHashMap<>();
        projects.forEach(project -> byProject.put(project.getId(), new RepositoryCounts(project)));

        long pullRequestCount = 0;
        if (!byProject.isEmpty()) {
            var pullRequests = pullRequestRepository.findByGithubProjectIdIn(new ArrayList<>(byProject.keySet()));
            pullRequestCount = pullRequests.size();
            pullRequests.forEach(pr -> byProject.get(pr.getGithubProject().getId()).pullRequests++);
        }

        var latestRuns = byProject.isEmpty() ? List.<com.mainuddin.codereview.entity.AnalysisRun>of()
                : runRepository.findLatestCompletedForOwner(githubId, AnalysisRunStatus.COMPLETED);
        LocalDateTime latestAnalysisAt = null;
        for (var run : latestRuns) {
            RepositoryCounts repository = byProject.get(run.getPullRequest().getGithubProject().getId());
            repository.analyzedPullRequests++;
            if (run.getCompletedAt() != null) {
                if (latestAnalysisAt == null || run.getCompletedAt().isAfter(latestAnalysisAt)) {
                    latestAnalysisAt = run.getCompletedAt();
                }
                if (repository.lastCompleted == null || run.getCompletedAt().isAfter(repository.lastCompleted)) {
                    repository.lastCompleted = run.getCompletedAt();
                }
            }
        }

        Map<String, Long> categories = counts("DESIGN", "DEFECT", "TEST", "REQUIREMENT",
                "DOCUMENTATION", "UNCLASSIFIED", "NOT_ASSESSED");
        Map<String, Long> severities = counts("1", "2", "3", "4", "5", "NOT_ASSESSED");
        long findingCount = 0;
        if (!latestRuns.isEmpty()) {
            var runProjects = new java.util.HashMap<Long, RepositoryCounts>();
            latestRuns.forEach(run -> runProjects.put(run.getId(),
                    byProject.get(run.getPullRequest().getGithubProject().getId())));
            var snapshots = findingRepository.findDashboardSnapshots(new ArrayList<>(runProjects.keySet()));
            findingCount = snapshots.size();
            for (var finding : snapshots) {
                runProjects.get(finding.analysisRunId()).findings++;
                String category = finding.debtCategory() != null ? finding.debtCategory() :
                        finding.categoryRuleVersion() == null ? "NOT_ASSESSED" : "UNCLASSIFIED";
                categories.merge(category, 1L, Long::sum);
                String severity = finding.severityScore() == null ? "NOT_ASSESSED" :
                        finding.severityScore().toString();
                severities.merge(severity, 1L, Long::sum);
            }
        }

        var stats = new DashboardStatsDTO(projects.size(), pullRequestCount, latestRuns.size(),
                findingCount, latestAnalysisAt);
        return new DashboardResponseDTO(stats, categories, severities,
                byProject.values().stream().map(RepositoryCounts::toDto).toList());
    }

    private static Map<String, Long> counts(String... keys) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : keys) result.put(key, 0L);
        return result;
    }
}
