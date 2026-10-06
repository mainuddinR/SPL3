package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.DashboardFindingDetailDTO;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.SATDFindingRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardFindingDetailService {
    private final SATDFindingRepository findings;
    private final AnalysisHistoryService history;

    public DashboardFindingDetailService(SATDFindingRepository findings, AnalysisHistoryService history) {
        this.findings = findings; this.history = history;
    }

    @Transactional(readOnly = true)
    public DashboardFindingDetailDTO get(Long findingId) {
        var owner = SecurityContextHolder.getContext().getAuthentication().getName();
        var finding = findings.findByIdAndAnalysisRunPullRequestGithubProjectUserGithubId(findingId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("SATD finding not found"));
        var run = finding.getAnalysisRun();
        var pr = run.getPullRequest();
        return new DashboardFindingDetailDTO(pr.getGithubProject().getFullName(), pr.getId(), pr.getNumber(),
                pr.getTitle(), run.getId(), run.getCompletedAt(), history.finding(pr.getId(), run.getId(), findingId));
    }
}
