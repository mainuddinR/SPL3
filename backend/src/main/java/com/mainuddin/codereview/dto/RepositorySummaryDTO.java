package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record RepositorySummaryDTO(Long id, String name, String url, long pullRequestCount,
                                   long analyzedPullRequestCount, long currentSatdFindingCount,
                                   LocalDateTime lastCompletedAnalysisAt) {}
