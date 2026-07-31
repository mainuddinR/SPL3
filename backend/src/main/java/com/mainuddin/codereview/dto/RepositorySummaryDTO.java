package com.mainuddin.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositorySummaryDTO {
    private String id;
    private String name;
    private String url;
    private String lastAnalyzed;
    private int healthScore;
    private String status;
}
