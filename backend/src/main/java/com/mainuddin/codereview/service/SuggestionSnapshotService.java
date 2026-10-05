package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.AnalysisRun;
import com.mainuddin.codereview.entity.AnalysisRunStatus;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.AnalysisRunRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import com.mainuddin.codereview.repository.SATDFindingRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestionSnapshotService {
    private final PullRequestRepository prs;
    private final AnalysisRunRepository runs;
    private final SATDFindingRepository findings;

    public SuggestionSnapshotService(PullRequestRepository prs, AnalysisRunRepository runs,
                                     SATDFindingRepository findings) {
        this.prs = prs; this.runs = runs; this.findings = findings;
    }

    @Transactional(readOnly = true)
    public Snapshot load(Long prId, Long runId, Long findingId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ResourceNotFoundException("Pull request not found");
        PullRequest pr = prs.findByIdAndGithubProjectUserGithubId(prId, auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Pull request not found"));
        AnalysisRun run = runs.findByIdAndPullRequestId(runId, prId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis run not found"));
        if (run.getStatus() != AnalysisRunStatus.COMPLETED) throw new ResourceNotFoundException("Analysis run not found");
        SATDFinding finding = findings.findByIdAndAnalysisRunId(findingId, runId)
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found"));
        return new Snapshot(pr.getGithubProject().getFullName(), finding.getId(), runId,
                finding.getFilename(), finding.getLineNumber(), finding.getCommentText(),
                finding.getPrecedingCode(), finding.getSucceedingCode(), finding.getDebtCategory(),
                finding.getCategoryRuleVersion(), finding.getCategoryReason(), finding.getSeverityScore(),
                finding.getSeverityRuleVersion(), finding.getSeverityReason(), finding.getMethodLength(),
                finding.getMethodComplexity(), finding.getAiSuggestion(), finding.getAiSuggestionGeneratedAt(),
                finding.getAiSuggestionProviderModel(), finding.getAiSuggestionPromptVersion());
    }

    public record Snapshot(String repository, Long findingId, Long runId, String filename, Integer lineNumber,
                           String comment, String preceding, String succeeding, String category,
                           String categoryRuleVersion, String categoryReason, Integer severity,
                           String severityRuleVersion, String severityReason, Integer methodLength,
                           Integer methodComplexity, String suggestion, java.time.LocalDateTime generatedAt,
                           String providerModel, String promptVersion) {}
}
