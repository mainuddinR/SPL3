package com.mainuddin.codereview.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class RiskEvidenceService {
    private record Rule(String type, String reason, Pattern pattern) {
        Rule(String type, String reason, String expression) {
            this(type, reason, Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
        }
    }

    // Require an admission of a missing, bypassed, or inadequate control, not a topic keyword alone.
    private static final String GAP = "(?:missing|absent|no|without|bypass(?:ed|ing)?|skip(?:ped|ping)?|disabled|inadequate|weak|broken|need(?:s)?|todo[: ]+add)";
    private static final List<Rule> RULES = List.of(
            new Rule("AUTHENTICATION", "Authentication control is described as missing or inadequate", gap("authentication")),
            new Rule("AUTHORIZATION", "Authorization or access control is described as missing or inadequate", gap("authorization|permission|access(?:[ -]control|[ -]check)?")),
            new Rule("INPUT_VALIDATION", "Input validation or injection protection is described as missing or inadequate", gap("input[ -]validation|validat(?:e|ion)|sanitiz(?:e|ation)|injection[ -]protection")),
            new Rule("CRYPTOGRAPHY", "Encryption or cryptographic control is described as missing or inadequate", gap("encrypt(?:ion)?|cryptograph(?:y|ic)")),
            new Rule("CREDENTIALS", "Credential or secret handling is described as inadequate", gap("credential(?:s)?|secret(?:s)?")),
            new Rule("SECURITY_CONTROL", "A security check or control is described as missing or inadequate", gap("security[ -]check|security[ -]control"))
    );

    private static String gap(String control) {
        String either = "(?:" + control + ")";
        return "\\b" + GAP + "\\s+(?:(?:proper|the|a|an)\\s+)?" + either + "\\b|\\b" + either +
                "\\s+(?:is|are)\\s+" + GAP + "\\b";
    }

    public RiskEvidence assess(String comment) {
        if (comment == null || comment.isBlank()) return null;
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(comment).find()) {
                return new RiskEvidence(rule.type(), rule.reason(), RiskEvidence.RULE_VERSION);
            }
        }
        return null;
    }
}
