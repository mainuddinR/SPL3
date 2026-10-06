package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record DashboardAnalyzedRowDTO(Long pullRequestId, Long repositoryId, String repository,
        Integer number, String title, Long runId, Integer analyzedCandidateCount,
        long satdFindingCount, LocalDateTime completedAt, String analyzedHeadSha) {}
