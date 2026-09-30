package com.mainuddin.codereview.dto;

import java.util.List;

public record AnalysisRunDetailDTO(
        AnalysisRunSummaryDTO run,
        List<SavedSatdFindingDTO> findings
) {}
