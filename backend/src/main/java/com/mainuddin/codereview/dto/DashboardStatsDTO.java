package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record DashboardStatsDTO(long repositoryCount, long pullRequestCount,
                                long analyzedPullRequestCount, long currentSatdFindingCount,
                                LocalDateTime latestAnalysisAt) {}
