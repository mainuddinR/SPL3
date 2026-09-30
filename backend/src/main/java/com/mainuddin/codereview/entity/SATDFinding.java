package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "satd_findings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SATDFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AnalysisRun analysisRun;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Lob
    @Column(name = "preceding_code")
    private String precedingCode;

    @Lob
    @Column(name = "succeeding_code")
    private String succeedingCode;

    @Column(name = "satd_probability", nullable = false)
    private Double satdProbability;

    @Column(name = "non_satd_probability", nullable = false)
    private Double nonSatdProbability;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "debt_category")
    private String debtCategory;

    @Column(name = "category_reason", columnDefinition = "TEXT")
    private String categoryReason;

    @Column(name = "category_rule_version")
    private String categoryRuleVersion;

    @Column(name = "severity_score")
    private Integer severityScore;

    @Column(name = "severity_reason", columnDefinition = "TEXT")
    private String severityReason;

    @Column(name = "severity_rule_version")
    private String severityRuleVersion;

    @Column(name = "method_length")
    private Integer methodLength;

    @Column(name = "method_complexity")
    private Integer methodComplexity;

    @Column(name = "method_metrics_rule_version")
    private String methodMetricsRuleVersion;

    @Column(name = "risk_evidence", columnDefinition = "TEXT")
    private String riskEvidence;

    @Column(name = "risk_evidence_rule_version")
    private String riskEvidenceRuleVersion;

    @Column(name = "security_flag")
    private Boolean securityFlag;

    @Column(name = "comment_text", columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;
}
