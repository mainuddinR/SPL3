package com.mainuddin.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "github_projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubProject {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "full_name", nullable = false)
    private String fullName;
    
    @Column(name = "html_url", nullable = false)
    private String htmlUrl;
    
    @Column(name = "owner_login", nullable = false)
    private String ownerLogin;
    
    @Column(name = "owner_avatar_url")
    private String ownerAvatarUrl;

    @Column(name = "language")
    private String language;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
