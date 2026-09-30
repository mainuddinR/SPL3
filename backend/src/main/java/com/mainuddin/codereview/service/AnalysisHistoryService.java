package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.AnalysisRunDetailDTO;
import com.mainuddin.codereview.dto.AnalysisRunSummaryDTO;
import com.mainuddin.codereview.dto.SavedSatdFindingDTO;
import com.mainuddin.codereview.entity.AnalysisRun;
import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.AnalysisRunRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import com.mainuddin.codereview.repository.SATDFindingRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisHistoryService {
    private final PullRequestRepository pullRequestRepository;
    private final AnalysisRunRepository runRepository;
    private final SATDFindingRepository findingRepository;

    public AnalysisHistoryService(PullRequestRepository pullRequestRepository,
                                  AnalysisRunRepository runRepository,
                                  SATDFindingRepository findingRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional(readOnly = true)
    public List<AnalysisRunSummaryDTO> list(Long prId) {
        verifyOwnership(prId);
        return runRepository.findByPullRequestIdOrderByStartedAtDescIdDesc(prId).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisRunDetailDTO detail(Long prId, Long runId) {
        verifyOwnership(prId);
        AnalysisRun run = runRepository.findByIdAndPullRequestId(runId, prId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis run not found"));
        List<SavedSatdFindingDTO> findings = findingRepository.findByAnalysisRunId(runId).stream()
                .map(this::finding)
                .toList();
        return new AnalysisRunDetailDTO(summary(run), findings);
    }

    private void verifyOwnership(Long prId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                pullRequestRepository.findByIdAndGithubProjectUserGithubId(prId, authentication.getName()).isEmpty()) {
            throw new ResourceNotFoundException("Pull request not found");
        }
    }

    private AnalysisRunSummaryDTO summary(AnalysisRun run) {
        return new AnalysisRunSummaryDTO(run.getId(), run.getStatus(), run.getStartedAt(),
                run.getCompletedAt(), run.getAnalyzedCandidateCount(), findingRepository.countByAnalysisRunId(run.getId()));
    }

    private SavedSatdFindingDTO finding(SATDFinding finding) {
        return new SavedSatdFindingDTO(finding.getFilename(), finding.getLineNumber(), finding.getCommentText(),
                finding.getPrecedingCode(), finding.getSucceedingCode(), "SATD", finding.getSatdProbability(),
                finding.getNonSatdProbability(), finding.getConfidence(),
                finding.getCategoryRuleVersion() == null ? "NOT_ASSESSED" :
                        finding.getDebtCategory() == null ? "UNCLASSIFIED" : finding.getDebtCategory(),
                finding.getCategoryReason(), finding.getCategoryRuleVersion(),
                finding.getSeverityRuleVersion() == null ? "LEGACY" :
                        finding.getSeverityScore() == null ? "NOT_ASSESSED" : "ASSESSED",
                finding.getSeverityScore(), finding.getSeverityReason(), finding.getSeverityRuleVersion(),
                finding.getMethodLength(), finding.getMethodComplexity(), finding.getMethodMetricsRuleVersion(),
                finding.getRiskEvidence(), finding.getRiskEvidenceRuleVersion());
    }
}
