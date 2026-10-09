package com.mainuddin.codereview.design;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.PatternOccurrence;
import com.mainuddin.codereview.entity.RepositoryPatternProfile;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.PatternOccurrenceRepository;
import com.mainuddin.codereview.repository.RepositoryPatternProfileRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class RepositoryPatternProfileService {
    private final GithubProjectRepository projects;
    private final RepositoryPatternProfileRepository profiles;
    private final PatternOccurrenceRepository occurrences;
    private final RepositorySourceSnapshotService snapshots;
    private final JavaTypeIndexBuilder indexBuilder;
    private final DesignPatternDetectionService detection;
    private final ObjectMapper json;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), new ThreadPoolExecutor.AbortPolicy());
    private final Map<Long, Long> activeByProject = new HashMap<>();
    private final Map<String, Long> activeByRevision = new HashMap<>();
    private final Map<Long, java.util.concurrent.CompletableFuture<Void>> completion = new HashMap<>();

    public RepositoryPatternProfileService(GithubProjectRepository projects,
            RepositoryPatternProfileRepository profiles, PatternOccurrenceRepository occurrences,
            RepositorySourceSnapshotService snapshots, JavaTypeIndexBuilder indexBuilder,
            DesignPatternDetectionService detection, ObjectMapper json) {
        this.projects = projects; this.profiles = profiles; this.occurrences = occurrences;
        this.snapshots = snapshots; this.indexBuilder = indexBuilder; this.detection = detection; this.json = json;
    }

    public GithubProject owned(Long projectId, String githubId) {
        return projects.findByIdAndUserGithubId(projectId, githubId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
    }

    public void ensureInitialProfile(Long projectId, String githubId) {
        if (defaultProfile(projectId) == null) start(projectId, githubId);
    }

    public synchronized PatternProfileDTO start(Long projectId, String githubId) {
        GithubProject project = owned(projectId, githubId);
        Long active = activeByProject.get(projectId);
        if (active != null) return toDto(profiles.findById(active).orElseThrow(), null);
        reconcileInterrupted(projectId);
        RepositoryPatternProfile profile = new RepositoryPatternProfile();
        profile.setProject(project); profile.setStatus("RUNNING"); profile.setProgress("Downloading repository snapshot...");
        profile = profiles.save(profile);
        Long profileId = profile.getId();
        activeByProject.put(projectId, profileId);
        try {
            executor.execute(() -> run(profileId, project, githubId, null));
        } catch (RuntimeException rejected) {
            activeByProject.remove(projectId);
            fail(profileId, "Design-profile capacity is full. Retry later.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Design-profile capacity is full. Retry later.");
        }
        return toDto(profile, null);
    }

    public PatternProfileDTO latest(Long projectId, String githubId) {
        GithubProject project = owned(projectId, githubId);
        reconcileInterrupted(projectId);
        return java.util.Optional.ofNullable(defaultProfile(projectId)).map(profile -> {
            String currentSha = null;
            if ("COMPLETED".equals(profile.getStatus())) {
                try { currentSha = snapshots.currentHead(project, githubId).sha(); }
                catch (RuntimeException ignored) { /* Saved profile remains readable while GitHub is unavailable. */ }
            }
            return toDto(profile, currentSha);
        }).orElse(null);
    }

    private RepositoryPatternProfile defaultProfile(Long projectId) {
        return profiles.findDefaultProfiles(projectId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    /** Authenticated exact immutable baseline. A null defaultBranch denotes a commit profile, not a branch refresh. */
    public synchronized RepositoryPatternProfile exactBase(Long projectId, String githubId, String sha) {
        GithubProject project = owned(projectId, githubId);
        if (sha == null || !sha.matches("[0-9a-fA-F]{40}"))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Sync PR details to obtain a valid base revision.");
        sha = sha.toLowerCase(java.util.Locale.ROOT);
        var saved = profiles.findFirstByProjectIdAndAnalyzedCommitShaAndStatusOrderByIdDesc(projectId, sha, "COMPLETED");
        if (saved.isPresent()) return saved.get();
        String key = projectId + ":" + sha;
        Long active = activeByRevision.get(key);
        if (active != null) return profiles.findById(active).orElseThrow();
        reconcileInterrupted(projectId);
        RepositoryPatternProfile profile = new RepositoryPatternProfile();
        profile.setProject(project); profile.setAnalyzedCommitSha(sha); profile.setStatus("RUNNING");
        profile.setProgress("Acquiring exact PR base source...");
        profile = profiles.save(profile);
        Long id = profile.getId();
        activeByRevision.put(key, id); completion.put(id, new java.util.concurrent.CompletableFuture<>());
        String immutableSha = sha;
        try { executor.execute(() -> run(id, project, githubId, immutableSha)); }
        catch (RuntimeException rejected) {
            activeByRevision.remove(key); completion.remove(id);
            fail(id, "Design-profile capacity is full. Retry later.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Design-profile capacity is full. Retry later.");
        }
        return profile;
    }

    public RepositoryPatternProfile awaitExactBase(Long profileId) {
        java.util.concurrent.CompletableFuture<Void> task;
        synchronized (this) { task = completion.get(profileId); }
        if (task != null) task.join();
        RepositoryPatternProfile profile = profiles.findById(profileId).orElseThrow();
        if (!"COMPLETED".equals(profile.getStatus())) {
            String message = profile.getFailureMessage();
            if (message != null && message.matches("[A-Z_]+\\|.*")) message = message.split("\\|", 4)[3];
            throw new DesignSourceException("EXACT_BASE_UNAVAILABLE", message == null
                    ? "Exact PR base analysis is unavailable. Retry the check." : message, null, null);
        }
        return profile;
    }

    public RepositoryPatternProfile latestCompleted(Long projectId) {
        return profiles.findFirstByProjectIdAndStatusOrderByIdDesc(projectId, "COMPLETED")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                        "Analyze repository design first."));
    }

    private void run(Long profileId, GithubProject project, String githubId, String exactSha) {
        try {
            var head = exactSha == null ? snapshots.currentHead(project, githubId)
                    : snapshots.exactCommit(project, githubId, exactSha);
            var snapshot = snapshots.capture(project, githubId, head, (done, total) -> {
                if (done == 0 || done == total || done % 10 == 0)
                    update(profileId, "Downloading Java files: " + done + " / " + total);
            });
            if (exactSha != null && !exactSha.equals(snapshot.sha()))
                throw new DesignSourceException("REVISION_MISMATCH", "Exact PR base source could not be verified at the requested commit.", null, null);
            update(profileId, "Parsing Java files...");
            var index = indexBuilder.build(snapshot.sources());
            update(profileId, "Building type relationships...");
            update(profileId, "Detecting patterns...");
            var candidates = detection.detect(index);
            update(profileId, "Saving design profile...");
            RepositoryPatternProfile profile = profiles.findById(profileId).orElseThrow();
            for (var candidate : candidates) {
                PatternOccurrence occurrence = new PatternOccurrence();
                occurrence.setProfile(profile);
                occurrence.setPatternType(candidate.patternType());
                occurrence.setPatternFamily(candidate.patternFamily());
                occurrence.setConfidence(candidate.confidence());
                occurrence.setRationale(candidate.rationale());
                occurrence.setConfidenceReason(candidate.confidenceReason());
                occurrence.setEvidenceJson(json.writeValueAsString(candidate.evidence()));
                occurrences.save(occurrence);
            }
            profile.setAnalyzedCommitSha(snapshot.sha()); profile.setDefaultBranch(exactSha == null ? snapshot.defaultBranch() : null);
            profile.setJavaFileCount(index.javaFileCount()); profile.setAnalyzedAt(LocalDateTime.now());
            profile.setStatus("COMPLETED"); profile.setProgress(PatternDiagnostics.from(index, candidates.size()).savedProgress());
            profiles.save(profile);
        } catch (DesignSourceException failure) {
            fail(profileId, failure.savedMessage());
        } catch (ResponseStatusException failure) {
            String message = switch (failure.getStatusCode().value()) {
                case 401 -> "Reconnect GitHub, then refresh the design profile.";
                case 429 -> "GitHub request limit reached. Refresh the profile later.";
                case 422 -> "Repository source exceeds the bounded design-profile limit or is incomplete.";
                default -> "Repository source is unavailable. Check GitHub access, then retry.";
            };
            fail(profileId, message);
        } catch (Exception failure) {
            fail(profileId, "Repository design analysis failed. Check GitHub access and repository size, then retry.");
        } finally {
            synchronized (this) {
                activeByProject.remove(project.getId(), profileId);
                if (exactSha != null) activeByRevision.remove(project.getId() + ":" + exactSha, profileId);
                var task = completion.remove(profileId);
                if (task != null) task.complete(null);
            }
        }
    }

    private void update(Long profileId, String stage) {
        RepositoryPatternProfile profile = profiles.findById(profileId).orElseThrow();
        profile.setProgress(stage); profiles.save(profile);
    }

    private void fail(Long profileId, String message) {
        RepositoryPatternProfile profile = profiles.findById(profileId).orElseThrow();
        profile.setStatus("FAILED"); profile.setProgress("Failed"); profile.setFailureMessage(message);
        profiles.save(profile);
    }

    private synchronized void reconcileInterrupted(Long projectId) {
        profiles.findByProjectIdAndStatus(projectId, "RUNNING").stream()
                .filter(profile -> !activeByProject.containsValue(profile.getId()) && !activeByRevision.containsValue(profile.getId()))
                .forEach(profile -> fail(profile.getId(), "Design analysis was interrupted. Retry the profile or PR check."));
    }

    public PatternProfileDTO toDto(RepositoryPatternProfile profile, String currentSha) {
        List<PatternProfileDTO.Occurrence> items = "COMPLETED".equals(profile.getStatus())
                ? occurrences.findByProfileIdOrderByIdAsc(profile.getId()).stream().map(this::toOccurrence).toList()
                : List.of();
        Boolean stale = currentSha == null || profile.getAnalyzedCommitSha() == null ? null
                : !profile.getAnalyzedCommitSha().equals(currentSha);
        String message = profile.getFailureMessage();
        String code = null; Long observed = null, limit = null;
        if (message != null && message.matches("[A-Z_]+\\|.*")) {
            String[] parts = message.split("\\|", 4);
            if (parts.length == 4) {
                code = parts[0];
                observed = parts[1].isEmpty() ? null : Long.valueOf(parts[1]);
                limit = parts[2].isEmpty() ? null : Long.valueOf(parts[2]);
                message = parts[3];
            }
        }
        return new PatternProfileDTO(profile.getId(), profile.getProject().getId(), profile.getStatus(),
                "COMPLETED".equals(profile.getStatus()) ? "Completed" : profile.getProgress(),
                profile.getAnalyzedCommitSha(), currentSha, stale, profile.getDefaultBranch(), profile.getAnalyzedAt(),
                profile.getJavaFileCount(), message, items, PatternDiagnostics.read(profile.getProgress()), code, observed, limit);
    }

    private PatternProfileDTO.Occurrence toOccurrence(PatternOccurrence occurrence) {
        try {
            List<PatternCandidate.Evidence> evidence = json.readValue(occurrence.getEvidenceJson(), new TypeReference<>() {});
            return new PatternProfileDTO.Occurrence(occurrence.getId(), occurrence.getPatternType(),
                    occurrence.getPatternFamily(), occurrence.getConfidence(), occurrence.getRationale(),
                    occurrence.getConfidenceReason(), evidence);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Saved pattern evidence is invalid", invalid);
        }
    }

    @PreDestroy synchronized void shutdown() {
        executor.shutdownNow();
        completion.values().forEach(task -> task.completeExceptionally(new IllegalStateException("Profile analysis interrupted")));
        completion.clear();
    }
}
