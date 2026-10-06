package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private PullRequest pullRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisRunStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "analyzed_candidate_count")
    private Integer analyzedCandidateCount;

    @Column(name = "analyzed_head_sha")
    private String analyzedHeadSha;

    @Column(name = "analyzed_base_sha")
    private String analyzedBaseSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false)
    @Builder.Default
    private ReviewPublicationStatus publicationStatus = ReviewPublicationStatus.NOT_PUBLISHED;

    @Column(name = "github_review_id")
    private Long githubReviewId;

    @Column(name = "github_review_url")
    private String githubReviewUrl;

    @Column(name = "review_published_at")
    private LocalDateTime reviewPublishedAt;

    @Column(name = "published_head_sha")
    private String publishedHeadSha;

    @Column(name = "publication_failure")
    private String publicationFailure;
}
