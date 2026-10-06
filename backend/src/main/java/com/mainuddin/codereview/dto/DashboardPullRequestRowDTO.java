package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record DashboardPullRequestRowDTO(Long id, Long repositoryId, String repository,
        Integer number, String title, String state, LocalDateTime updatedAt,
        boolean analyzed, Long latestCompletedRunId, LocalDateTime latestCompletedAt,
        long currentSatdFindingCount) {}
