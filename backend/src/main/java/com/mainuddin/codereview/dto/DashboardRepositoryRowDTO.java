package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record DashboardRepositoryRowDTO(Long id, String fullName, String url,
        long pullRequestCount, long analyzedPullRequestCount, long currentSatdFindingCount,
        LocalDateTime lastCompletedAnalysisAt) {}
