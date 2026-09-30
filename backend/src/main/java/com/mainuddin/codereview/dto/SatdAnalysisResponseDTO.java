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
    private String debtCategory;
    private String categoryReason;
    private String categoryRuleVersion;
    private String severityState;
    private Integer severityScore;
    private String severityReason;
    private String severityRuleVersion;
    private Integer methodLength;
    private Integer methodComplexity;
    private String methodMetricsRuleVersion;
    private String riskEvidence;
    private String riskEvidenceRuleVersion;
}
