package com.mainuddin.codereview.service;

public record SeverityAssessment(String state, Integer score, String reason, String ruleVersion,
                                 Integer methodLength, Integer complexity, String methodMetricsRuleVersion,
                                 String riskEvidence, String riskEvidenceRuleVersion) {
    public static final String RULE_VERSION = "severity-rules-v1";

    public static SeverityAssessment notAssessed(String reason) {
        return new SeverityAssessment("NOT_ASSESSED", null, reason, RULE_VERSION, null, null, null, null, null);
    }

    public static SeverityAssessment notApplicable() {
        return new SeverityAssessment("NOT_APPLICABLE", null, null, null, null, null, null, null, null);
    }
}
