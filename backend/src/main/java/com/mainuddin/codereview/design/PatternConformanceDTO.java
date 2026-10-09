package com.mainuddin.codereview.design;

import java.time.LocalDateTime;
import java.util.List;

public record PatternConformanceDTO(Long id, Long pullRequestId, Long profileId, String status, String progress,
        String failureMessage, String checkedHeadSha, String checkedBaseSha, LocalDateTime checkedAt, List<Finding> findings, Summary summary,
        Integer changedJavaFileCount) {
    public PatternConformanceDTO(Long id, Long pullRequestId, Long profileId, String status, String progress,
            String failureMessage, String checkedHeadSha, String checkedBaseSha, LocalDateTime checkedAt, List<Finding> findings) {
        this(id, pullRequestId, profileId, status, progress, failureMessage, checkedHeadSha, checkedBaseSha, checkedAt,
                findings, "COMPLETED".equals(status) ? Summary.from(findings) : null, null);
    }
    public record Summary(String overallStatus, long conforms, long potentialDeviations, long notApplicable, long insufficientEvidence) {
        public static Summary from(List<Finding> findings) {
            long conforms = count(findings, "CONFORMS"), deviations = count(findings, "POTENTIAL_DEVIATION"),
                    irrelevant = count(findings, "NOT_APPLICABLE"), insufficient = count(findings, "INSUFFICIENT_EVIDENCE");
            String overall = deviations > 0 ? "POTENTIAL_DEVIATION" : insufficient > 0 ? "INSUFFICIENT_EVIDENCE"
                    : conforms > 0 ? "CONFORMS" : "NOT_APPLICABLE";
            return new Summary(overall, conforms, deviations, irrelevant, insufficient);
        }
        private static long count(List<Finding> findings, String status) { return findings.stream().filter(f -> status.equals(f.status())).count(); }
    }
    public record Finding(String patternType, String patternFamily, String status, String confidence,
            String file, Integer line, String className, String rationale, String recommendation,
            List<PatternCandidate.Evidence> evidence, String structuralChange, String designImpact,
            String reviewPriority, Advice aiSuggestion) {
        public Finding(String patternType, String patternFamily, String status, String confidence,
                String file, Integer line, String className, String rationale, String recommendation,
                List<PatternCandidate.Evidence> evidence) {
            this(patternType, patternFamily, status, confidence, file, line, className, rationale, recommendation,
                    evidence, null, null, null, null);
        }
        public Finding withAdvice(Advice advice) {
            return new Finding(patternType, patternFamily, status, confidence, file, line, className, rationale,
                    recommendation, evidence, structuralChange, designImpact, reviewPriority, advice);
        }
    }
    public record Advice(String summary, String risk, String recommendation, String verification, String limitations,
            LocalDateTime generatedAt, String providerModel, String promptVersion) {}
}
