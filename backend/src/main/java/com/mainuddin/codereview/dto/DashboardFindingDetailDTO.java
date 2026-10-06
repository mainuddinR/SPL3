package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record DashboardFindingDetailDTO(String repository, Long pullRequestId, int pullRequestNumber,
        String pullRequestTitle, Long runId, LocalDateTime completedAt, SavedSatdFindingDTO finding) {}
