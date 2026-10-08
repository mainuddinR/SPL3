package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "repository_pattern_profiles")
@Getter @Setter
public class RepositoryPatternProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false)
    private GithubProject project;
    @Column(name = "analyzed_commit_sha", length = 40)
    private String analyzedCommitSha;
    @Column(name = "default_branch", length = 100)
    private String defaultBranch;
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;
    @Column(name = "java_file_count")
    private Integer javaFileCount;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "progress", nullable = false)
    private String progress;
    @Column(name = "failure_message")
    private String failureMessage;
}
