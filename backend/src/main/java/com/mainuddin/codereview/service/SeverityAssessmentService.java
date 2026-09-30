package com.mainuddin.codereview.service;

import org.springframework.stereotype.Service;

@Service
public class SeverityAssessmentService {
    public SeverityAssessment assess(MethodMetricsResult metrics, RiskEvidence risk) {
        if (metrics == null || metrics.status() != MethodMetricsResult.Status.AVAILABLE ||
                metrics.methodLength() == null || metrics.complexity() == null ||
                metrics.methodLength() < 0 || metrics.complexity() < 1) {
            return SeverityAssessment.notAssessed("Complete method metrics are unavailable");
        }
        int length = metrics.methodLength();
        int complexity = metrics.complexity();
        boolean security = risk != null;
        boolean elevated = length > 50 || complexity > 10;
        int score;
        String reason;
        if (length > 100 && complexity > 20 && security) {
            score = 5;
            reason = "Extreme method length and complexity with explicit security-control debt";
        } else if ((length > 50 && complexity > 10) || (elevated && security)) {
            score = 4;
            reason = "High combined method metrics or elevated metric with explicit security-control debt";
        } else if (elevated || security) {
            score = 3;
            reason = "Elevated method metric or explicit security-control debt";
        } else if (length > 20 || complexity > 5) {
            score = 2;
            reason = "Moderate method length or complexity";
        } else {
            score = 1;
            reason = "Low method length and complexity without explicit security-control debt";
        }
        return new SeverityAssessment("ASSESSED", score, reason, SeverityAssessment.RULE_VERSION,
                length, complexity, metrics.metricsRuleVersion(), risk == null ? null : risk.reason(),
                risk == null ? null : risk.ruleVersion());
    }
}
