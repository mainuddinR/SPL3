package com.mainuddin.codereview.service;

public record CategoryAssessment(String category, String reason, String ruleVersion) {
    public static final String RULE_VERSION = "category-rules-v1";

    public static CategoryAssessment unclassified(String reason) {
        return new CategoryAssessment("UNCLASSIFIED", reason, RULE_VERSION);
    }

    public static CategoryAssessment notAssessed() {
        return new CategoryAssessment(null, null, null);
    }
}
