package com.mainuddin.codereview.service;

public record RiskEvidence(String signalType, String reason, String ruleVersion) {
    public static final String RULE_VERSION = "risk-evidence-v1";
}
