package com.mainuddin.codereview.dto;

public record SavedSatdFindingDTO(
        String filename,
        Integer lineNumber,
        String commentText,
        String precedingCode,
        String succeedingCode,
        String label,
        Double satdProbability,
        Double nonSatdProbability,
        Double confidence,
        String debtCategory,
        String categoryReason,
        String categoryRuleVersion,
        String severityState,
        Integer severityScore,
        String severityReason,
        String severityRuleVersion,
        Integer methodLength,
        Integer methodComplexity,
        String methodMetricsRuleVersion,
        String riskEvidence,
        String riskEvidenceRuleVersion
) {}
