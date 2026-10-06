package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record DashboardFindingRowDTO(Long id, Long repositoryId, String repository,
        Long pullRequestId, Integer pullRequestNumber, String pullRequestTitle, Long runId,
        String filename, Integer lineNumber, String commentText, Double satdProbability,
        Double confidence, String debtCategory, String categoryReason, Integer severityScore,
        String severityReason, boolean aiSuggestionAvailable, LocalDateTime completedAt) {}
