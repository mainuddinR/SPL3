package com.mainuddin.codereview.design;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.entity.PrPatternCheck;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.entity.RepositoryPatternProfile;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.PatternOccurrenceRepository;
import com.mainuddin.codereview.repository.PrPatternCheckRepository;
import com.mainuddin.codereview.repository.PullRequestFileRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class PatternConformanceService {
    private final PullRequestRepository prs;
    private final PullRequestFileRepository files;
    private final RepositoryPatternProfileService profiles;
    private final PatternOccurrenceRepository occurrences;
    private final PrPatternCheckRepository checks;
    private final RepositorySourceSnapshotService snapshots;
    private final JavaTypeIndexBuilder indexBuilder;
    private final PatternConformanceEvaluator evaluator;
    private final ObjectMapper json;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), new ThreadPoolExecutor.AbortPolicy());
    private final Map<Long, Long> activeByPr = new HashMap<>();

    public PatternConformanceService(PullRequestRepository prs, PullRequestFileRepository files,
            RepositoryPatternProfileService profiles, PatternOccurrenceRepository occurrences,
            PrPatternCheckRepository checks, RepositorySourceSnapshotService snapshots,
            JavaTypeIndexBuilder indexBuilder, PatternConformanceEvaluator evaluator, ObjectMapper json) {
        this.prs = prs; this.files = files; this.profiles = profiles; this.occurrences = occurrences;
        this.checks = checks; this.snapshots = snapshots; this.indexBuilder = indexBuilder;
        this.evaluator = evaluator; this.json = json;
    }

    public synchronized PatternConformanceDTO start(Long prId, String githubId) {
        PullRequest pr = owned(prId, githubId);
        Long active = activeByPr.get(prId);
        if (active != null) return toDto(checks.findById(active).orElseThrow());
        reconcileInterrupted(prId);
        if (pr.getHeadSha() == null || pr.getBaseSha() == null
                || !pr.getHeadSha().equals(pr.getSyncedFilesHeadSha())
                || !pr.getBaseSha().equals(pr.getSyncedFilesBaseSha()))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Sync PR details for this revision before checking design conformance.");
        RepositoryPatternProfile profile = profiles.exactBase(pr.getGithubProject().getId(), githubId, pr.getBaseSha());
        PrPatternCheck check = new PrPatternCheck();
        check.setPullRequest(pr); check.setProfile(profile); check.setStatus("RUNNING");
        check.setProgress("Preparing exact PR base design profile..."); check.setCheckedAt(LocalDateTime.now());
        check.setCheckedHeadSha(pr.getHeadSha()); check.setCheckedBaseSha(pr.getBaseSha());
        check = checks.save(check);
        Long checkId = check.getId();
        activeByPr.put(prId, checkId);
        List<PullRequestFile> changedFiles = files.findByPullRequestId(prId);
        try {
            executor.execute(() -> run(checkId, pr, profile, changedFiles, githubId));
        } catch (RuntimeException rejected) {
            activeByPr.remove(prId);
            fail(checkId, "Design-conformance capacity is full. Retry later.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Design-conformance capacity is full. Retry later.");
        }
        return toDto(check);
    }

    public PatternConformanceDTO latest(Long prId, String githubId) {
        owned(prId, githubId);
        reconcileInterrupted(prId);
        return checks.findFirstByPullRequestIdOrderByIdDesc(prId).map(this::toDto).orElse(null);
    }

    public Long repositoryId(Long prId, String githubId) {
        return owned(prId, githubId).getGithubProject().getId();
    }

    private PullRequest owned(Long prId, String githubId) {
        return prs.findByIdAndGithubProjectUserGithubId(prId, githubId)
                .orElseThrow(() -> new ResourceNotFoundException("Pull request not found"));
    }

    private void run(Long checkId, PullRequest pr, RepositoryPatternProfile profile,
            List<PullRequestFile> files, String githubId) {
        try {
            profile = profiles.awaitExactBase(profile.getId());
            List<PatternCandidate> baseline = new ArrayList<>();
            for (var occurrence : occurrences.findByProfileIdOrderByIdAsc(profile.getId())) {
                List<PatternCandidate.Evidence> evidence = json.readValue(occurrence.getEvidenceJson(), new TypeReference<>() {});
                baseline.add(new PatternCandidate(occurrence.getPatternType(), occurrence.getPatternFamily(),
                        occurrence.getConfidence(), occurrence.getRationale(), occurrence.getConfidenceReason(), evidence));
            }
            boolean matchesBase = profile.getAnalyzedCommitSha() != null && profile.getAnalyzedCommitSha().equals(pr.getBaseSha());
            List<PatternConformanceDTO.Finding> findings;
            if (!matchesBase) findings = evaluator.evaluate(baseline, indexBuilder.build(Map.of()), List.of(), false);
            else {
                var sources = snapshots.changedJavaSources(pr.getGithubProject(), githubId, files);
                var baseSources = snapshots.changedJavaBaseSources(pr.getGithubProject(), githubId, files, pr.getBaseSha());
                update(checkId, "Comparing changed base/head Java structure with saved occurrences...");
                findings = evaluator.evaluate(baseline, indexBuilder.build(sources), indexBuilder.build(baseSources), files, true);
            }
            PrPatternCheck check = checks.findById(checkId).orElseThrow();
            check.setFindingsJson(json.writeValueAsString(findings));
            check.setStatus("COMPLETED"); check.setProgress("Completed|JAVA_FILES=" + files.stream()
                    .filter(f -> RepositorySourceSnapshotService.allowedJavaPath(f.getFilename())).count()); checks.save(check);
        } catch (DesignSourceException failure) {
            fail(checkId, failure.getReason());
        } catch (ResponseStatusException failure) {
            String message = switch (failure.getStatusCode().value()) {
                case 401 -> "Reconnect GitHub, then run Pattern Check again.";
                case 429 -> "GitHub request limit reached. Retry Pattern Check later.";
                case 422 -> "Changed Java source is too large or incomplete for this check.";
                default -> "Design-conformance check failed. Check GitHub access and synced PR details.";
            };
            fail(checkId, message);
        } catch (Exception failure) {
            fail(checkId, "Design-conformance check failed. Check GitHub access and synced PR details, then retry.");
        } finally {
            synchronized (this) { activeByPr.remove(pr.getId(), checkId); }
        }
    }

    private void update(Long checkId, String stage) {
        PrPatternCheck check = checks.findById(checkId).orElseThrow();
        check.setProgress(stage); checks.save(check);
    }

    private void fail(Long checkId, String message) {
        PrPatternCheck check = checks.findById(checkId).orElseThrow();
        check.setStatus("FAILED"); check.setProgress("Failed"); check.setFailureMessage(message);
        checks.save(check);
    }

    private synchronized void reconcileInterrupted(Long prId) {
        if (activeByPr.containsKey(prId)) return;
        checks.findFirstByPullRequestIdOrderByIdDesc(prId)
                .filter(check -> "RUNNING".equals(check.getStatus()))
                .ifPresent(check -> fail(check.getId(), "Design check was interrupted. Run Pattern Check again."));
    }

    private PatternConformanceDTO toDto(PrPatternCheck check) {
        List<PatternConformanceDTO.Finding> findings = List.of();
        if ("COMPLETED".equals(check.getStatus()) && check.getFindingsJson() != null) {
            try { findings = json.readValue(check.getFindingsJson(), new TypeReference<>() {}); }
            catch (JsonProcessingException invalid) { throw new IllegalStateException("Saved design check is invalid", invalid); }
        }
        if ("COMPLETED".equals(check.getStatus()) && (check.getProfile().getAnalyzedCommitSha() == null
                || !check.getProfile().getAnalyzedCommitSha().equals(check.getCheckedBaseSha()))) {
            // Historical empty checks must not hide an unsafe baseline mismatch. No new source/API call is made.
            findings = List.of(new PatternConformanceDTO.Finding("REPOSITORY_BASELINE", "ARCHITECTURAL",
                    "INSUFFICIENT_EVIDENCE", "MEDIUM", null, null, null,
                    "The saved repository profile does not match this check's PR base revision.",
                    "Analyze the matching baseline before interpreting conformance.", List.of()));
        }
        Integer changedCount = check.getProgress() != null && check.getProgress().matches("Completed\\|JAVA_FILES=\\d+")
                ? Integer.valueOf(check.getProgress().substring("Completed|JAVA_FILES=".length())) : null;
        return new PatternConformanceDTO(check.getId(), check.getPullRequest().getId(), check.getProfile().getId(),
                check.getStatus(), "COMPLETED".equals(check.getStatus()) ? "Completed" : check.getProgress(), check.getFailureMessage(), check.getCheckedHeadSha(),
                check.getCheckedBaseSha(), check.getCheckedAt(), findings,
                "COMPLETED".equals(check.getStatus()) ? PatternConformanceDTO.Summary.from(findings) : null, changedCount);
    }

    @PreDestroy void shutdown() { executor.shutdownNow(); }
}
