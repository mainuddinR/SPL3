package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pull_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_pr_id", nullable = false)
    private Long githubPrId;

    @Column(nullable = false)
    private Integer number;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String state;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "head_sha")
    private String headSha;

    @Column(name = "base_sha")
    private String baseSha;

    @Column(name = "synced_files_head_sha")
    private String syncedFilesHeadSha;

    @Column(name = "synced_files_base_sha")
    private String syncedFilesBaseSha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private GithubProject githubProject;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
