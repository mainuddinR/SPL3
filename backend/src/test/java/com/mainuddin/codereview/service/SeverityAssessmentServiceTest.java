package com.mainuddin.codereview.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeverityAssessmentServiceTest {
    private final SeverityAssessmentService service = new SeverityAssessmentService();
    private final RiskEvidence risk = new RiskEvidence("AUTHENTICATION", "Missing authentication", RiskEvidence.RULE_VERSION);

    private SeverityAssessment assess(int length, int complexity, boolean withRisk) {
        return service.assess(new MethodMetricsResult(MethodMetricsResult.Status.AVAILABLE, "m",
                MethodMetricsResult.Kind.METHOD, 1, 5, length, complexity, JavaMethodMetricsService.RULE_VERSION),
                withRisk ? risk : null);
    }

    @Test void exactMetricBoundariesAndHighestRuleWin() {
        assertEquals(1, assess(20, 5, false).score());
        assertEquals(2, assess(21, 5, false).score());
        assertEquals(2, assess(20, 6, false).score());
        assertEquals(2, assess(50, 10, false).score());
        assertEquals(3, assess(51, 5, false).score());
        assertEquals(3, assess(20, 11, false).score());
        assertEquals(3, assess(20, 5, true).score());
        assertEquals(4, assess(51, 11, false).score());
        assertEquals(4, assess(51, 5, true).score());
        assertEquals(4, assess(20, 11, true).score());
        assertEquals(4, assess(100, 20, true).score());
        assertEquals(5, assess(101, 21, true).score());
        assertEquals(4, assess(101, 21, false).score());
        assertEquals(5, assess(101, 21, true).score());
    }

    @Test void missingMetricsStayUnavailableAndAssessmentIsVersioned() {
        var unavailable = service.assess(MethodMetricsResult.unavailable(MethodMetricsResult.Status.METHOD_NOT_FOUND), risk);
        assertEquals("NOT_ASSESSED", unavailable.state());
        assertNull(unavailable.score());
        assertNull(unavailable.methodLength());
        var assessed = assess(20, 5, true);
        assertEquals(SeverityAssessment.RULE_VERSION, assessed.ruleVersion());
        assertEquals(JavaMethodMetricsService.RULE_VERSION, assessed.methodMetricsRuleVersion());
        assertEquals(RiskEvidence.RULE_VERSION, assessed.riskEvidenceRuleVersion());
        assertEquals(assessed, assess(20, 5, true));
    }
}
