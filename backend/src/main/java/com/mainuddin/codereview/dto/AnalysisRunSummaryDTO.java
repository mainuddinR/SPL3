package com.mainuddin.codereview.dto;

import com.mainuddin.codereview.entity.AnalysisRunStatus;
import java.time.LocalDateTime;

public record AnalysisRunSummaryDTO(
        Long id,
        AnalysisRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer analyzedCandidateCount,
        long satdFindingCount
) {}
