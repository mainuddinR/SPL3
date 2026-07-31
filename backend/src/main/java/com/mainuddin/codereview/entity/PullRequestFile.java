package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequestFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sha;

    private String filename;

    private String status;

    private Integer additions;

    private Integer deletions;

    private Integer changes;

    @Column(columnDefinition = "TEXT")
    private String patch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private PullRequest pullRequest;
}
