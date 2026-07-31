package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestComment;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.repository.PullRequestCommentRepository;
import com.mainuddin.codereview.repository.PullRequestFileRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import com.mainuddin.codereview.service.GithubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PullRequestController {

    private final GithubService githubService;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestFileRepository pullRequestFileRepository;
    private final PullRequestCommentRepository pullRequestCommentRepository;

    public PullRequestController(GithubService githubService, 
                                 PullRequestRepository pullRequestRepository,
                                 PullRequestFileRepository pullRequestFileRepository,
                                 PullRequestCommentRepository pullRequestCommentRepository) {
        this.githubService = githubService;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestFileRepository = pullRequestFileRepository;
        this.pullRequestCommentRepository = pullRequestCommentRepository;
    }

    @PostMapping("/project/{projectId}/sync")
    public ResponseEntity<?> syncPullRequests(@PathVariable Long projectId) {
        githubService.fetchAndStorePullRequests(projectId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<PullRequest>> getPullRequests(@PathVariable Long projectId) {
        return ResponseEntity.ok(pullRequestRepository.findByGithubProjectId(projectId));
    }

    @GetMapping("/{prId}")
    public ResponseEntity<PullRequest> getPullRequest(@PathVariable Long prId) {
        return pullRequestRepository.findById(prId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{prId}/sync-details")
    public ResponseEntity<?> syncPullRequestDetails(@PathVariable Long prId) {
        githubService.fetchAndStorePullRequestDetails(prId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{prId}/files")
    public ResponseEntity<List<PullRequestFile>> getPullRequestFiles(@PathVariable Long prId) {
        return ResponseEntity.ok(pullRequestFileRepository.findByPullRequestId(prId));
    }

    @GetMapping("/{prId}/comments")
    public ResponseEntity<List<PullRequestComment>> getPullRequestComments(@PathVariable Long prId) {
        return ResponseEntity.ok(pullRequestCommentRepository.findByPullRequestId(prId));
    }
}
