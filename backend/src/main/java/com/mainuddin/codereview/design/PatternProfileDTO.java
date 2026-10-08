package com.mainuddin.codereview.design;

import java.time.LocalDateTime;
import java.util.List;

public record PatternProfileDTO(Long id, Long projectId, String status, String progress,
        String analyzedCommitSha, String currentDefaultBranchSha, Boolean stale, String defaultBranch,
        LocalDateTime analyzedAt, Integer javaFileCount, String failureMessage,
        List<Occurrence> occurrences, PatternDiagnostics diagnostics, String failureCode, Long observed, Long limit, RepositoryDesignSummary designSummary) {
    public PatternProfileDTO(Long id, Long projectId, String status, String progress,
            String analyzedCommitSha, String currentDefaultBranchSha, Boolean stale, String defaultBranch,
            LocalDateTime analyzedAt, Integer javaFileCount, String failureMessage, List<Occurrence> occurrences,
            PatternDiagnostics diagnostics, String failureCode, Long observed, Long limit) {
        this(id, projectId, status, progress, analyzedCommitSha, currentDefaultBranchSha, stale, defaultBranch,
                analyzedAt, javaFileCount, failureMessage, occurrences, diagnostics, failureCode, observed, limit,
                "COMPLETED".equals(status) ? RepositoryDesignSummary.from(occurrences, javaFileCount) : null);
    }
    public PatternProfileDTO(Long id, Long projectId, String status, String progress, String analyzedCommitSha,
            String currentDefaultBranchSha, Boolean stale, String defaultBranch, LocalDateTime analyzedAt,
            Integer javaFileCount, String failureMessage, List<Occurrence> occurrences) {
        this(id, projectId, status, progress, analyzedCommitSha, currentDefaultBranchSha, stale, defaultBranch,
                analyzedAt, javaFileCount, failureMessage, occurrences, null, null, null, null);
    }
    public record Occurrence(Long id, String patternType, String patternFamily, String confidence,
            String rationale, String confidenceReason, List<PatternCandidate.Evidence> evidence) {}
}
