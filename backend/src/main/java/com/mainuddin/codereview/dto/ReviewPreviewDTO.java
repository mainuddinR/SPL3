package com.mainuddin.codereview.dto;

import com.mainuddin.codereview.entity.ReviewPublicationStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewPreviewDTO(Long runId, String repository, Integer pullRequestNumber,
        Integer analyzedCandidateCount, int satdFindingCount, int inlineCommentCount,
        int unmappableCount, boolean staleRevision, ReviewPublicationStatus publicationStatus,
        String summary, List<InlinePreview> inlineComments, String githubReviewUrl,
        LocalDateTime reviewPublishedAt, String publicationFailure) {
    public record InlinePreview(String path, int line, String body) {}
}
