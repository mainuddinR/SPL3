package com.mainuddin.codereview.service;

public record SuggestionDraft(String summary, String risk, String recommendation,
                              String verification, String limitations) {
    public String formatted() {
        if (!valid(summary, 300) || !valid(risk, 400) || !valid(recommendation, 700)
                || !valid(verification, 400) || !valid(limitations, 400)
                || recommendation.isBlank()
                || summary.length() + risk.length() + recommendation.length()
                   + verification.length() + limitations.length() > 2000) {
            throw new IllegalArgumentException("Invalid suggestion output");
        }
        return "Summary\n" + summary + "\n\nRisk\n" + risk + "\n\nRecommendation\n"
                + recommendation + "\n\nVerification\n" + verification + "\n\nLimitations\n" + limitations;
    }

    private static boolean valid(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit;
    }
}
