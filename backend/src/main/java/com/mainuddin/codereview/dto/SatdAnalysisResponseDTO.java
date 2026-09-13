package com.mainuddin.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatdAnalysisResponseDTO {
    private String filename;
    private Integer lineNumber;
    private String commentText;
    private String precedingCode;
    private String succeedingCode;
    
    // ML Prediction
    private String label;
    private Double satdProbability;
    private Double nonSatdProbability;
    private Double confidence;
}
