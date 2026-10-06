package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.AnalysisProgressDTO;
import com.mainuddin.codereview.dto.SatdAnalysisResponseDTO;
import com.mainuddin.codereview.entity.AnalysisRun;
import com.mainuddin.codereview.entity.AnalysisRunStatus;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.AnalysisRunRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import com.mainuddin.codereview.repository.SATDFindingRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class ProgressiveAnalysisService {
    private final PullRequestRepository prs;
    private final AnalysisRunRepository runs;
    private final SATDFindingRepository findings;
    private final AnalysisInputSnapshotService snapshots;
    private final AnalysisRunPersistenceService persistence;
    private final PullRequestAnalysisService analysis;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());
    private final Map<Long, Job> jobs = new HashMap<>();
    private final Map<Long, Long> activeByPr = new HashMap<>();
    private final LocalDateTime appStartedAt = LocalDateTime.now();

    public ProgressiveAnalysisService(PullRequestRepository prs, AnalysisRunRepository runs,
            SATDFindingRepository findings, AnalysisInputSnapshotService snapshots,
            AnalysisRunPersistenceService persistence, PullRequestAnalysisService analysis) {
        this.prs = prs; this.runs = runs; this.findings = findings;
        this.snapshots = snapshots; this.persistence = persistence; this.analysis = analysis;
    }

    public synchronized AnalysisProgressDTO start(Long prId) {
        verifyOwner(prId);
        Long active = activeByPr.get(prId);
        if (active != null) return progress(prId, active, 0);
        reconcileOrphans(prId);
        AnalysisInputSnapshotService.Snapshot input = snapshots.capture(prId);
        Long runId = persistence.start(input.pullRequest());
        Job job = new Job(runId);
        jobs.put(runId, job);
        activeByPr.put(prId, runId);
        trimFinishedJobs();
        try {
            executor.execute(() -> {
                try {
                    analysis.process(input, runId, job);
                    job.finish(AnalysisRunStatus.COMPLETED, null);
                } catch (RuntimeException ex) {
                    job.finish(AnalysisRunStatus.FAILED, "Analysis failed. Retry after checking the analysis service.");
                } finally {
                    synchronized (ProgressiveAnalysisService.this) { activeByPr.remove(prId, runId); }
                }
            });
        } catch (RuntimeException rejected) {
            activeByPr.remove(prId);
            jobs.remove(runId);
            persistence.fail(runId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analysis capacity is full. Retry later.");
        }
        return job.snapshot(0);
    }

    public synchronized AnalysisProgressDTO current(Long prId) {
        verifyOwner(prId);
        reconcileOrphans(prId);
        var run = runs.findFirstByPullRequestIdAndStatusOrderByIdDesc(prId, AnalysisRunStatus.RUNNING);
        return run.filter(value -> jobs.containsKey(value.getId()))
                .map(value -> progress(prId, value.getId(), 0)).orElse(null);
    }

    public AnalysisProgressDTO progress(Long prId, Long runId, int after) {
        verifyOwner(prId);
        if (after < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        AnalysisRun run = runs.findByIdAndPullRequestId(runId, prId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis run not found"));
        Job job;
        synchronized (this) { job = jobs.get(runId); }
        if (job != null) return job.snapshot(after);
        if (run.getStatus() == AnalysisRunStatus.RUNNING &&
                (run.getStartedAt() == null || run.getStartedAt().isBefore(appStartedAt))) {
            persistence.fail(runId);
            run.setStatus(AnalysisRunStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
        }
        int count = run.getAnalyzedCandidateCount() == null ? 0 : run.getAnalyzedCandidateCount();
        int satd = (int) findings.countByAnalysisRunId(runId);
        return new AnalysisProgressDTO(runId, run.getStatus(), count, count, satd,
                Math.max(0, count - satd), run.getStatus() == AnalysisRunStatus.COMPLETED ? 100 : 0,
                after, run.getCompletedAt(), run.getStatus() == AnalysisRunStatus.FAILED
                    ? "Analysis was interrupted or failed. Retry analysis." : null, List.of());
    }

    private void verifyOwner(Long prId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() ||
                prs.findByIdAndGithubProjectUserGithubId(prId, auth.getName()).isEmpty())
            throw new ResourceNotFoundException("Pull request not found");
    }

    private synchronized void reconcileOrphans(Long prId) {
        for (AnalysisRun run : runs.findByPullRequestId(prId)) {
            if (run.getStatus() == AnalysisRunStatus.RUNNING && !jobs.containsKey(run.getId())
                    && (run.getStartedAt() == null || run.getStartedAt().isBefore(appStartedAt)))
                persistence.fail(run.getId());
        }
    }

    private synchronized void trimFinishedJobs() {
        if (jobs.size() <= 20) return;
        jobs.entrySet().removeIf(entry -> !activeByPr.containsValue(entry.getKey()) && jobs.size() > 20);
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    private static final class Job implements PullRequestAnalysisService.ProgressObserver {
        private final Long runId;
        private final List<SatdAnalysisResponseDTO> results = new ArrayList<>();
        private int total;
        private int satd;
        private AnalysisRunStatus status = AnalysisRunStatus.RUNNING;
        private LocalDateTime completedAt;
        private String failureMessage;
        private Job(Long runId) { this.runId = runId; }
        @Override public synchronized void total(int value) { total = value; }
        @Override public synchronized void result(SatdAnalysisResponseDTO result) {
            results.add(result);
            if ("SATD".equals(result.getLabel())) satd++;
        }
        synchronized void finish(AnalysisRunStatus value, String message) {
            status = value; failureMessage = message; completedAt = LocalDateTime.now();
        }
        synchronized AnalysisProgressDTO snapshot(int after) {
            int cursor = Math.min(after, results.size());
            int end = Math.min(results.size(), cursor + 50);
            int processed = results.size();
            return new AnalysisProgressDTO(runId, status, total, processed, satd, processed - satd,
                    status == AnalysisRunStatus.COMPLETED ? 100 : total == 0 ? 0 : processed * 100 / total,
                    end, completedAt, failureMessage, List.copyOf(results.subList(cursor, end)));
        }
    }
}
