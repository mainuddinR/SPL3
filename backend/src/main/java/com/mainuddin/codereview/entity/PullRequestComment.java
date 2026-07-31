package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "pull_request_comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_comment_id")
    private Long githubCommentId;

    private String path;

    private Integer position;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "user_login")
    private String userLogin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private PullRequest pullRequest;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
