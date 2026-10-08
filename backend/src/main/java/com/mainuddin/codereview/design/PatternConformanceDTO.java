package com.mainuddin.codereview.design;

import java.time.LocalDateTime;
import java.util.List;

public record PatternConformanceDTO(Long id, Long pullRequestId, Long profileId, String status, String progress,
        String failureMessage, String checkedHeadSha, String checkedBaseSha, LocalDateTime checkedAt, List<Finding> findings, Summary summary) {
    public PatternConformanceDTO(Long id, Long pullRequestId, Long profileId, String status, String progress,
            String failureMessage, String checkedHeadSha, String checkedBaseSha, LocalDateTime checkedAt, List<Finding> findings) {
        this(id, pullRequestId, profileId, status, progress, failureMessage, checkedHeadSha, checkedBaseSha, checkedAt,
                findings, "COMPLETED".equals(status) ? Summary.from(findings) : null);
    }
    public record Summary(String overallStatus, long conforms, long potentialDeviations, long notApplicable, long insufficientEvidence) {
        public static Summary from(List<Finding> findings) {
            long conforms = count(findings, "CONFORMS"), deviations = count(findings, "POTENTIAL_DEVIATION"),
                    irrelevant = count(findings, "NOT_APPLICABLE"), insufficient = count(findings, "INSUFFICIENT_EVIDENCE");
            String overall = deviations > 0 ? "POTENTIAL_DEVIATION" : conforms > 0 ? "CONFORMS"
                    : insufficient > 0 ? "INSUFFICIENT_EVIDENCE" : "NOT_APPLICABLE";
            return new Summary(overall, conforms, deviations, irrelevant, insufficient);
        }
        private static long count(List<Finding> findings, String status) { return findings.stream().filter(f -> status.equals(f.status())).count(); }
    }
    public record Finding(String patternType, String patternFamily, String status, String confidence,
            String file, Integer line, String className, String rationale, String recommendation,
            List<PatternCandidate.Evidence> evidence) {}
}
