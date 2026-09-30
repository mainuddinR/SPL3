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

import com.mainuddin.codereview.dto.SatdAnalysisResponseDTO;
import com.mainuddin.codereview.service.PullRequestAnalysisService;
import com.mainuddin.codereview.service.AnalysisHistoryService;
import com.mainuddin.codereview.dto.AnalysisRunSummaryDTO;
import com.mainuddin.codereview.dto.AnalysisRunDetailDTO;

@RestController
@RequestMapping("/api/prs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PullRequestController {

    private final GithubService githubService;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestFileRepository pullRequestFileRepository;
    private final PullRequestCommentRepository pullRequestCommentRepository;
    private final PullRequestAnalysisService pullRequestAnalysisService;
    private final AnalysisHistoryService analysisHistoryService;

    public PullRequestController(GithubService githubService, 
                                 PullRequestRepository pullRequestRepository,
                                 PullRequestFileRepository pullRequestFileRepository,
                                 PullRequestCommentRepository pullRequestCommentRepository,
                                 PullRequestAnalysisService pullRequestAnalysisService,
                                 AnalysisHistoryService analysisHistoryService) {
        this.githubService = githubService;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestFileRepository = pullRequestFileRepository;
        this.pullRequestCommentRepository = pullRequestCommentRepository;
        this.pullRequestAnalysisService = pullRequestAnalysisService;
        this.analysisHistoryService = analysisHistoryService;
    }

    @PostMapping("/{prId}/analyze")
    public ResponseEntity<List<SatdAnalysisResponseDTO>> analyzePullRequest(@PathVariable Long prId) {
        List<SatdAnalysisResponseDTO> analysisResults = pullRequestAnalysisService.analyzePullRequest(prId);
        return ResponseEntity.ok(analysisResults);
    }

    @GetMapping("/{prId}/analysis-runs")
    public ResponseEntity<List<AnalysisRunSummaryDTO>> getAnalysisRuns(@PathVariable Long prId) {
        return ResponseEntity.ok(analysisHistoryService.list(prId));
    }

    @GetMapping("/{prId}/analysis-runs/{runId}")
    public ResponseEntity<AnalysisRunDetailDTO> getAnalysisRun(@PathVariable Long prId, @PathVariable Long runId) {
        return ResponseEntity.ok(analysisHistoryService.detail(prId, runId));
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
