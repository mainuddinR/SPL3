package com.mainuddin.codereview.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskEvidenceServiceTest {
    private final RiskEvidenceService service = new RiskEvidenceService();

    @Test void explicitControlAdmissionsProduceTypedEvidence() {
        assertEquals("AUTHENTICATION", service.assess("// missing authentication").signalType());
        assertEquals("AUTHORIZATION", service.assess("// no authorization check").signalType());
        assertEquals("INPUT_VALIDATION", service.assess("// input validation is missing").signalType());
        assertEquals("CREDENTIALS", service.assess("// weak credential handling").signalType());
        assertEquals("CRYPTOGRAPHY", service.assess("// encryption is disabled").signalType());
        assertEquals("SECURITY_CONTROL", service.assess("// missing security check").signalType());
        assertEquals(RiskEvidence.RULE_VERSION, service.assess("// no authentication").ruleVersion());
    }

    @Test void topicWordsAloneAreNotEvidenceAndResultsRepeat() {
        assertNull(service.assess("// security"));
        assertNull(service.assess("// authentication documentation"));
        assertNull(service.assess("// validate output before printing"));
        assertEquals(service.assess("// missing authentication"), service.assess("// missing authentication"));
    }
}
