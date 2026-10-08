package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "pr_pattern_checks")
@Getter @Setter
public class PrPatternCheck {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pull_request_id", nullable = false)
    private PullRequest pullRequest;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "profile_id", nullable = false)
    private RepositoryPatternProfile profile;
    @Column(name = "checked_head_sha", length = 40)
    private String checkedHeadSha;
    @Column(name = "checked_base_sha", length = 40)
    private String checkedBaseSha;
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "progress", nullable = false)
    private String progress;
    @Column(name = "failure_message")
    private String failureMessage;
    @Lob @Column(name = "findings_json")
    private String findingsJson;
}
