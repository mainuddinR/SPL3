package com.mainuddin.codereview.dto;

import com.mainuddin.codereview.entity.AnalysisRunStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AnalysisProgressDTO(Long runId, AnalysisRunStatus status, int totalCandidates,
        int processedCandidates, int satdCount, int nonSatdCount, int progressPercent,
        int nextCursor, LocalDateTime completedAt, String failureMessage,
        List<SatdAnalysisResponseDTO> results) {}
