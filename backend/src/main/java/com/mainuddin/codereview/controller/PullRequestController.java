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
import com.mainuddin.codereview.dto.SuggestionResponseDTO;
import com.mainuddin.codereview.service.SuggestionGenerationService;
import com.mainuddin.codereview.service.ReviewPublishingService;
import com.mainuddin.codereview.dto.ReviewPreviewDTO;
import com.mainuddin.codereview.dto.AnalysisProgressDTO;
import com.mainuddin.codereview.service.ProgressiveAnalysisService;

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
    private final SuggestionGenerationService suggestionGenerationService;
    private final ReviewPublishingService reviewPublishingService;
    private final ProgressiveAnalysisService progressiveAnalysisService;

    public PullRequestController(GithubService githubService, 
                                 PullRequestRepository pullRequestRepository,
                                 PullRequestFileRepository pullRequestFileRepository,
                                 PullRequestCommentRepository pullRequestCommentRepository,
                                 PullRequestAnalysisService pullRequestAnalysisService,
                                 AnalysisHistoryService analysisHistoryService,
                                 SuggestionGenerationService suggestionGenerationService,
                                 ReviewPublishingService reviewPublishingService,
                                 ProgressiveAnalysisService progressiveAnalysisService) {
        this.githubService = githubService;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestFileRepository = pullRequestFileRepository;
        this.pullRequestCommentRepository = pullRequestCommentRepository;
        this.pullRequestAnalysisService = pullRequestAnalysisService;
        this.analysisHistoryService = analysisHistoryService;
        this.suggestionGenerationService = suggestionGenerationService;
        this.reviewPublishingService = reviewPublishingService;
        this.progressiveAnalysisService = progressiveAnalysisService;
    }

    @PostMapping("/{prId}/analyze")
    public ResponseEntity<List<SatdAnalysisResponseDTO>> analyzePullRequest(@PathVariable Long prId) {
        List<SatdAnalysisResponseDTO> analysisResults = pullRequestAnalysisService.analyzePullRequest(prId);
        return ResponseEntity.ok(analysisResults);
    }

    @PostMapping("/{prId}/analysis-runs")
    public AnalysisProgressDTO startProgressiveAnalysis(@PathVariable Long prId) {
        return progressiveAnalysisService.start(prId);
    }

    @GetMapping("/{prId}/analysis-runs/current")
    public AnalysisProgressDTO currentProgressiveAnalysis(@PathVariable Long prId) {
        return progressiveAnalysisService.current(prId);
    }

    @GetMapping("/{prId}/analysis-runs/{runId}/progress")
    public AnalysisProgressDTO getProgress(@PathVariable Long prId, @PathVariable Long runId,
            @RequestParam(defaultValue = "0") int after) {
        return progressiveAnalysisService.progress(prId, runId, after);
    }

    @GetMapping("/{prId}/analysis-runs")
    public ResponseEntity<List<AnalysisRunSummaryDTO>> getAnalysisRuns(@PathVariable Long prId) {
        return ResponseEntity.ok(analysisHistoryService.list(prId));
    }

    @GetMapping("/{prId}/analysis-runs/{runId}")
    public ResponseEntity<AnalysisRunDetailDTO> getAnalysisRun(@PathVariable Long prId, @PathVariable Long runId) {
        return ResponseEntity.ok(analysisHistoryService.detail(prId, runId));
    }

    @GetMapping("/{prId}/analysis-runs/{runId}/review-preview")
    public ResponseEntity<ReviewPreviewDTO> previewReview(@PathVariable Long prId, @PathVariable Long runId) {
        return ResponseEntity.ok(reviewPublishingService.preview(prId, runId));
    }

    @PostMapping("/{prId}/analysis-runs/{runId}/publish-review")
    public ResponseEntity<ReviewPreviewDTO> publishReview(@PathVariable Long prId, @PathVariable Long runId,
            @RequestBody PublishConfirmation confirmation) {
        return ResponseEntity.ok(reviewPublishingService.publish(prId, runId, confirmation.confirm()));
    }

    public record PublishConfirmation(boolean confirm) {}

    @PostMapping("/{prId}/analysis-runs/{runId}/findings/{findingId}/suggestion")
    public ResponseEntity<SuggestionResponseDTO> generateSuggestion(@PathVariable Long prId,
            @PathVariable Long runId, @PathVariable Long findingId) {
        return ResponseEntity.ok(suggestionGenerationService.generate(prId, runId, findingId));
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
