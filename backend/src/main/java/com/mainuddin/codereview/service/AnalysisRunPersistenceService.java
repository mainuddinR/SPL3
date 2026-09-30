package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.AnalysisRun;
import com.mainuddin.codereview.entity.AnalysisRunStatus;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.repository.AnalysisRunRepository;
import com.mainuddin.codereview.repository.SATDFindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnalysisRunPersistenceService {
    private final AnalysisRunRepository runRepository;
    private final SATDFindingRepository findingRepository;

    public AnalysisRunPersistenceService(AnalysisRunRepository runRepository, SATDFindingRepository findingRepository) {
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(PullRequest pr) {
        AnalysisRun run = AnalysisRun.builder()
                .pullRequest(pr)
                .status(AnalysisRunStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .analyzedHeadSha(pr.getSyncedFilesHeadSha())
                .analyzedBaseSha(pr.getSyncedFilesBaseSha())
                .build();
        return runRepository.saveAndFlush(run).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long runId, int analyzedCandidateCount, List<SATDFinding> findings) {
        AnalysisRun run = runRepository.findById(runId).orElseThrow();
        findings.forEach(finding -> finding.setAnalysisRun(run));
        findingRepository.saveAllAndFlush(findings);
        run.setAnalyzedCandidateCount(analyzedCandidateCount);
        run.setCompletedAt(LocalDateTime.now());
        run.setStatus(AnalysisRunStatus.COMPLETED);
        runRepository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long runId) {
        AnalysisRun run = runRepository.findById(runId).orElseThrow();
        run.setStatus(AnalysisRunStatus.FAILED);
        run.setCompletedAt(LocalDateTime.now());
        runRepository.saveAndFlush(run);
    }
}
