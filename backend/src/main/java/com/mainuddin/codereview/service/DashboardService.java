package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.DashboardResponseDTO;
import com.mainuddin.codereview.dto.DashboardStatsDTO;
import com.mainuddin.codereview.dto.RepositorySummaryDTO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class DashboardService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.mainuddin.codereview.repository.UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.mainuddin.codereview.repository.GithubProjectRepository githubProjectRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.mainuddin.codereview.repository.PullRequestRepository pullRequestRepository;

    public DashboardResponseDTO getDashboardData(String githubId) {
        com.mainuddin.codereview.entity.User user = userRepository.findByGithubId(githubId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<com.mainuddin.codereview.entity.GithubProject> projects = githubProjectRepository.findByUserId(user.getId());

        List<RepositorySummaryDTO> repos = projects.stream().map(p -> RepositorySummaryDTO.builder()
                .id(String.valueOf(p.getId()))
                .name(p.getName())
                .url(p.getHtmlUrl())
                .lastAnalyzed("Not analyzed")
                .healthScore(100)
                .status("Healthy")
                .build()
        ).toList();

        long totalPrs = pullRequestRepository.count();

        DashboardStatsDTO stats = DashboardStatsDTO.builder()
                .totalRepositories(projects.size())
                .totalPullRequests((int) totalPrs)
                .criticalIssues(0)
                .technicalDebtScore(100)
                .build();

        return DashboardResponseDTO.builder()
                .stats(stats)
                .repositories(repos)
                .build();
    }
}
