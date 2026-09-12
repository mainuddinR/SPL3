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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_file_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private PullRequestFile pullRequestFile;

    @Column(name = "debt_category", nullable = false)
    private String debtCategory;

    @Column(name = "severity_score")
    private Integer severityScore;

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
