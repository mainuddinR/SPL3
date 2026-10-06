package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.*;
import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.repository.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardDrilldownService {
    private final DashboardService summary;
    private final GithubProjectRepository projects;
    private final PullRequestRepository prs;
    private final AnalysisRunRepository runs;
    private final SATDFindingRepository findings;

    public DashboardDrilldownService(DashboardService summary, GithubProjectRepository projects,
            PullRequestRepository prs, AnalysisRunRepository runs, SATDFindingRepository findings) {
        this.summary = summary; this.projects = projects; this.prs = prs; this.runs = runs; this.findings = findings;
    }

    @Transactional(readOnly = true)
    public DashboardPageDTO<DashboardRepositoryRowDTO> repositories(String owner, int page, int size) {
        validatePage(page, size);
        Map<Long, RepositorySummaryDTO> counts = summary.getDashboardData(owner).repositories().stream()
                .collect(Collectors.toMap(RepositorySummaryDTO::id, row -> row));
        List<DashboardRepositoryRowDTO> rows = projects.findByUserGithubIdOrderByIdAsc(owner).stream()
                .map(project -> {
                    RepositorySummaryDTO stats = counts.get(project.getId());
                    return new DashboardRepositoryRowDTO(project.getId(), project.getFullName(), project.getHtmlUrl(),
                            stats.pullRequestCount(), stats.analyzedPullRequestCount(), stats.currentSatdFindingCount(),
                            stats.lastCompletedAnalysisAt());
                }).toList();
        return slice(rows, page, size);
    }

    @Transactional(readOnly = true)
    public DashboardPageDTO<DashboardPullRequestRowDTO> pullRequests(String owner, int page, int size,
            Long repositoryId, String state, Boolean analyzed, String query) {
        validatePage(page, size);
        Map<Long, AnalysisRun> current = currentRuns(owner).stream()
                .collect(Collectors.toMap(run -> run.getPullRequest().getId(), run -> run));
        String q = normalized(query);
        Integer number = parseNumber(q);
        Specification<PullRequest> spec = (root, criteria, cb) -> {
            Join<PullRequest, GithubProject> project = root.join("githubProject");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(project.get("user").get("githubId"), owner));
            if (repositoryId != null) predicates.add(cb.equal(project.get("id"), repositoryId));
            if (state != null && !state.isBlank()) predicates.add(cb.equal(cb.lower(root.get("state")), state.toLowerCase(Locale.ROOT)));
            if (analyzed != null) {
                var exists = criteria.subquery(Long.class);
                var run = exists.from(AnalysisRun.class);
                exists.select(run.get("id")).where(cb.equal(run.get("pullRequest"), root),
                        cb.equal(run.get("status"), AnalysisRunStatus.COMPLETED));
                predicates.add(analyzed ? cb.exists(exists) : cb.not(cb.exists(exists)));
            }
            if (q != null) {
                Predicate text = cb.or(cb.like(cb.lower(root.get("title")), "%" + q + "%"),
                        cb.like(cb.lower(project.get("fullName")), "%" + q + "%"));
                predicates.add(number == null ? text : cb.or(text, cb.equal(root.get("number"), number)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = prs.findAll(spec, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        var rows = result.getContent().stream().map(pr -> {
            AnalysisRun run = current.get(pr.getId());
            return new DashboardPullRequestRowDTO(pr.getId(), pr.getGithubProject().getId(),
                    pr.getGithubProject().getFullName(), pr.getNumber(), pr.getTitle(), pr.getState(),
                    pr.getUpdatedAt(), run != null, run == null ? null : run.getId(),
                    run == null ? null : run.getCompletedAt(),
                    run == null ? 0 : findings.countByAnalysisRunId(run.getId()));
        }).toList();
        return new DashboardPageDTO<>(rows, result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public DashboardPageDTO<DashboardAnalyzedRowDTO> analyzed(String owner, int page, int size, Long repositoryId) {
        validatePage(page, size);
        List<AnalysisRun> current = currentRuns(owner).stream()
                .filter(run -> repositoryId == null || Objects.equals(repositoryId, run.getPullRequest().getGithubProject().getId()))
                .sorted(Comparator.comparing(AnalysisRun::getId).reversed()).toList();
        long offset = (long) (page - 1) * size;
        if (offset >= current.size()) return new DashboardPageDTO<>(List.of(), current.size(), page, size);
        List<DashboardAnalyzedRowDTO> rows = current.subList((int) offset, (int) Math.min(current.size(), offset + size)).stream().map(run -> {
            PullRequest pr = run.getPullRequest();
            return new DashboardAnalyzedRowDTO(pr.getId(), pr.getGithubProject().getId(),
                    pr.getGithubProject().getFullName(), pr.getNumber(), pr.getTitle(), run.getId(),
                    run.getAnalyzedCandidateCount(), findings.countByAnalysisRunId(run.getId()),
                    run.getCompletedAt(), run.getAnalyzedHeadSha());
        }).toList();
        return new DashboardPageDTO<>(rows, current.size(), page, size);
    }

    @Transactional(readOnly = true)
    public DashboardPageDTO<DashboardFindingRowDTO> findings(String owner, int page, int size,
            Long repositoryId, String category, String severity, String aiSuggestion, String query) {
        validatePage(page, size);
        validateFindingFilters(category, severity, aiSuggestion);
        List<Long> currentIds = currentRuns(owner).stream().map(AnalysisRun::getId).toList();
        if (currentIds.isEmpty()) return new DashboardPageDTO<>(List.of(), 0, page, size);
        String q = normalized(query);
        Specification<SATDFinding> spec = (root, criteria, cb) -> {
            Join<SATDFinding, AnalysisRun> run = root.join("analysisRun");
            Join<AnalysisRun, PullRequest> pr = run.join("pullRequest");
            Join<PullRequest, GithubProject> project = pr.join("githubProject");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(run.get("id").in(currentIds));
            predicates.add(cb.equal(project.get("user").get("githubId"), owner));
            if (repositoryId != null) predicates.add(cb.equal(project.get("id"), repositoryId));
            if (category != null && !category.isBlank()) {
                predicates.add(switch (category) {
                    case "NOT_ASSESSED" -> cb.isNull(root.get("categoryRuleVersion"));
                    case "UNCLASSIFIED" -> cb.and(cb.isNotNull(root.get("categoryRuleVersion")), cb.isNull(root.get("debtCategory")));
                    default -> cb.equal(root.get("debtCategory"), category);
                });
            }
            if (severity != null && !severity.isBlank()) predicates.add("NOT_ASSESSED".equals(severity)
                    ? cb.isNull(root.get("severityScore")) : cb.equal(root.get("severityScore"), Integer.parseInt(severity)));
            if ("GENERATED".equals(aiSuggestion)) predicates.add(cb.isNotNull(root.get("aiSuggestion")));
            if ("NOT_GENERATED".equals(aiSuggestion)) predicates.add(cb.isNull(root.get("aiSuggestion")));
            if (q != null) predicates.add(cb.or(cb.like(cb.lower(root.get("filename")), "%" + q + "%"),
                    cb.like(cb.lower(root.get("commentText")), "%" + q + "%"),
                    cb.like(cb.lower(pr.get("title")), "%" + q + "%")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = findings.findAll(spec, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        var rows = result.getContent().stream().map(finding -> {
            AnalysisRun run = finding.getAnalysisRun();
            PullRequest pr = run.getPullRequest();
            String debtCategory = finding.getDebtCategory() != null ? finding.getDebtCategory()
                    : finding.getCategoryRuleVersion() == null ? "NOT_ASSESSED" : "UNCLASSIFIED";
            return new DashboardFindingRowDTO(finding.getId(), pr.getGithubProject().getId(),
                    pr.getGithubProject().getFullName(), pr.getId(), pr.getNumber(), pr.getTitle(),
                    run.getId(), finding.getFilename(), finding.getLineNumber(), finding.getCommentText(),
                    finding.getSatdProbability(), finding.getConfidence(), debtCategory,
                    finding.getCategoryReason(), finding.getSeverityScore(), finding.getSeverityReason(),
                    finding.getAiSuggestion() != null, run.getCompletedAt());
        }).toList();
        return new DashboardPageDTO<>(rows, result.getTotalElements(), page, size);
    }

    private List<AnalysisRun> currentRuns(String owner) {
        return runs.findLatestCompletedForOwner(owner, AnalysisRunStatus.COMPLETED);
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
    }

    private static void validateFindingFilters(String category, String severity, String ai) {
        if (category != null && !category.isBlank() && !Set.of("DESIGN", "DEFECT", "TEST", "REQUIREMENT",
                "DOCUMENTATION", "UNCLASSIFIED", "NOT_ASSESSED").contains(category))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category");
        if (severity != null && !severity.isBlank() && !Set.of("1", "2", "3", "4", "5", "NOT_ASSESSED").contains(severity))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity");
        if (ai != null && !ai.isBlank() && !Set.of("ANY", "GENERATED", "NOT_GENERATED").contains(ai))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid suggestion filter");
    }

    private static String normalized(String query) {
        if (query == null || query.isBlank()) return null;
        String value = query.trim().toLowerCase(Locale.ROOT);
        return value.substring(0, Math.min(100, value.length()));
    }

    private static Integer parseNumber(String value) {
        try { return value == null ? null : Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static <T> DashboardPageDTO<T> slice(List<T> rows, int page, int size) {
        long offset = (long) (page - 1) * size;
        if (offset >= rows.size()) return new DashboardPageDTO<>(List.of(), rows.size(), page, size);
        return new DashboardPageDTO<>(rows.subList((int) offset, (int) Math.min(rows.size(), offset + size)), rows.size(), page, size);
    }
}
