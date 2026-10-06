package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.ReviewPreviewDTO;
import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ReviewPublishingService {
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");
    private static final Pattern SENSITIVE = Pattern.compile("(?i)(token|secret|password|api[_-]?key|credential|private[_-]?key)\\s*[:=]|gh[pousr]_[A-Za-z0-9]{20,}|bearer\\s+[A-Za-z0-9._-]{12,}|sk-[A-Za-z0-9_-]{12,}|AKIA[A-Z0-9]{16}|BEGIN [A-Z ]*PRIVATE KEY");
    private final PullRequestRepository prs;
    private final AnalysisRunRepository runs;
    private final SATDFindingRepository findings;
    private final PullRequestFileRepository files;
    private final GitHubReviewClient github;
    private final TransactionTemplate transactions;

    public ReviewPublishingService(PullRequestRepository prs, AnalysisRunRepository runs,
            SATDFindingRepository findings, PullRequestFileRepository files, GitHubReviewClient github,
            PlatformTransactionManager manager) {
        this.prs = prs; this.runs = runs; this.findings = findings; this.files = files;
        this.github = github; this.transactions = new TransactionTemplate(manager);
    }

    public ReviewPreviewDTO preview(Long prId, Long runId) {
        Snapshot snapshot = load(prId, runId);
        boolean stale = snapshot.run().getPublicationStatus() == ReviewPublicationStatus.NOT_PUBLISHED
                && isStale(snapshot);
        return render(snapshot, stale);
    }

    public ReviewPreviewDTO publish(Long prId, Long runId, boolean confirmed) {
        if (!confirmed) throw new ReviewPublishException(HttpStatus.BAD_REQUEST, "Review publication requires confirmation.");
        Snapshot snapshot = load(prId, runId);
        if (snapshot.run().getPublicationStatus() != ReviewPublicationStatus.NOT_PUBLISHED) {
            return render(snapshot, false);
        }
        if (isStale(snapshot)) throw new ReviewPublishException(HttpStatus.CONFLICT,
                "The PR revision changed. Sync details and run analysis again before publishing.");
        ReviewPreviewDTO preview = render(snapshot, false);
        Integer claimed = transactions.execute(status -> runs.claimReview(runId, AnalysisRunStatus.COMPLETED,
                ReviewPublicationStatus.NOT_PUBLISHED, ReviewPublicationStatus.PUBLISHING));
        if (claimed == null || claimed != 1) return render(load(prId, runId), false);

        try {
            List<GitHubReviewClient.ReviewComment> comments = preview.inlineComments().stream()
                    .map(item -> new GitHubReviewClient.ReviewComment(item.path(), item.line(), "RIGHT", item.body()))
                    .toList();
            GitHubReviewClient.PublishedReview published = github.publish(snapshot.githubId(), snapshot.owner(),
                    snapshot.repo(), snapshot.pr().getNumber(), snapshot.run().getAnalyzedHeadSha(),
                    preview.summary(), comments);
            transactions.executeWithoutResult(status -> {
                AnalysisRun run = runs.findById(runId).orElseThrow();
                run.setGithubReviewId(published.id());
                run.setGithubReviewUrl(safeReviewUrl(published.htmlUrl()));
                run.setReviewPublishedAt(LocalDateTime.now());
                run.setPublishedHeadSha(snapshot.run().getAnalyzedHeadSha());
                run.setPublicationFailure(null);
                run.setPublicationStatus(ReviewPublicationStatus.PUBLISHED);
            });
            return render(load(prId, runId), false);
        } catch (RuntimeException ex) {
            // An ambiguous provider response must never trigger an automatic duplicate review.
            transactions.executeWithoutResult(status -> {
                AnalysisRun run = runs.findById(runId).orElseThrow();
                run.setPublicationStatus(ReviewPublicationStatus.PUBLISH_FAILED);
                run.setPublicationFailure(ex instanceof ReviewPublishException ? ex.getMessage()
                        : "Publication result uncertain. Check GitHub before any retry.");
            });
            if (ex instanceof ReviewPublishException known) throw known;
            throw new ReviewPublishException(HttpStatus.BAD_GATEWAY,
                    "Publication result uncertain. Check GitHub before any retry.");
        }
    }

    private Snapshot load(Long prId, Long runId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ResourceNotFoundException("Pull request not found");
        PullRequest pr = prs.findByIdAndGithubProjectUserGithubId(prId, auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Pull request not found"));
        AnalysisRun run = runs.findByIdAndPullRequestId(runId, prId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis run not found"));
        if (run.getStatus() != AnalysisRunStatus.COMPLETED) throw new ReviewPublishException(HttpStatus.CONFLICT,
                "Only a completed analysis run can be published.");
        GithubProject project = pr.getGithubProject();
        return new Snapshot(auth.getName(), pr, run, findings.findByAnalysisRunId(runId),
                files.findByPullRequestId(prId), project.getFullName(), project.getOwnerLogin(), project.getName());
    }

    private boolean isStale(Snapshot s) {
        String sha = s.run().getAnalyzedHeadSha();
        if (sha == null || sha.isBlank() || !sha.equals(s.pr().getSyncedFilesHeadSha())
                || !sha.equals(s.pr().getHeadSha())) return true;
        return !sha.equals(github.currentHead(s.githubId(), s.owner(), s.repo(), s.pr().getNumber()));
    }

    private ReviewPreviewDTO render(Snapshot s, boolean stale) {
        List<ReviewPreviewDTO.InlinePreview> inline = new ArrayList<>();
        List<String> unmappable = new ArrayList<>();
        Map<String, Long> categories = s.findings().stream().filter(f -> f.getDebtCategory() != null)
                .collect(Collectors.groupingBy(SATDFinding::getDebtCategory, TreeMap::new, Collectors.counting()));
        Map<Integer, Long> severities = s.findings().stream().filter(f -> f.getSeverityScore() != null)
                .collect(Collectors.groupingBy(SATDFinding::getSeverityScore, TreeMap::new, Collectors.counting()));
        boolean patchesMatch = Objects.equals(s.run().getAnalyzedHeadSha(), s.pr().getSyncedFilesHeadSha());
        for (SATDFinding finding : s.findings()) {
            PullRequestFile file = s.files().stream().filter(f -> Objects.equals(f.getFilename(), finding.getFilename()))
                    .findFirst().orElse(null);
            if (patchesMatch && file != null && onRight(file.getPatch(), finding.getLineNumber())) {
                inline.add(new ReviewPreviewDTO.InlinePreview(finding.getFilename(), finding.getLineNumber(),
                        comment(finding)));
            } else {
                unmappable.add("- " + safe(finding.getFilename(), 200) + ":" + finding.getLineNumber()
                        + " (category: " + safe(finding.getDebtCategory(), 40) + ", severity: "
                        + (finding.getSeverityScore() == null ? "not assessed" : finding.getSeverityScore() + "/5") + ")");
            }
        }
        StringBuilder body = new StringBuilder("Automated Code Review — SATD analysis\n\n")
                .append("Candidate comments analyzed: ").append(s.run().getAnalyzedCandidateCount()).append("\n")
                .append("SATD findings: ").append(s.findings().size()).append("\n")
                .append("Analyzed head SHA: ").append(safe(s.run().getAnalyzedHeadSha(), 40)).append("\n")
                .append("Category distribution: ").append(categories).append("\n")
                .append("Severity distribution: ").append(severities).append("\n")
                .append("Inline findings: ").append(inline.size()).append("\n");
        if (!unmappable.isEmpty()) body.append("\nSATD findings without inline diff location:\n")
                .append(String.join("\n", unmappable)).append("\n");
        body.append("\nAutomated technical-debt assessment. Review findings before acting.");
        return new ReviewPreviewDTO(s.run().getId(), s.fullName(), s.pr().getNumber(),
                s.run().getAnalyzedCandidateCount(), s.findings().size(), inline.size(), unmappable.size(),
                stale, s.run().getPublicationStatus(), body.toString(), inline,
                s.run().getGithubReviewUrl(), s.run().getReviewPublishedAt(), s.run().getPublicationFailure());
    }

    private String comment(SATDFinding finding) {
        StringBuilder body = new StringBuilder("SATD detected\n\nCategory: ")
                .append(safe(finding.getDebtCategory(), 40)).append("\nSeverity: ")
                .append(finding.getSeverityScore() == null ? "Not assessed" : finding.getSeverityScore() + "/5")
                .append("\nConfidence: ").append(String.format(Locale.ROOT, "%.1f%%", finding.getConfidence() * 100));
        append(body, "Comment", finding.getCommentText(), 500);
        append(body, "Reason", finding.getCategoryReason(), 300);
        if (finding.getAiSuggestion() != null) {
            String suggestion = finding.getAiSuggestion();
            int end = suggestion.indexOf("\n\n");
            append(body, "Saved suggested remediation", end < 0 ? suggestion : suggestion.substring(0, end), 300);
        }
        return body.toString();
    }

    private void append(StringBuilder body, String heading, String raw, int limit) {
        if (raw != null && !raw.isBlank()) body.append("\n\n").append(heading).append(":\n").append(safe(raw, limit));
    }

    private String safe(String raw, int limit) {
        if (raw == null || raw.isBlank()) return "Not assessed";
        String value = raw.length() > limit ? raw.substring(0, limit) + "…" : raw;
        return SENSITIVE.matcher(value).find() ? "[omitted: sensitive text]" : value;
    }

    private String safeReviewUrl(String raw) {
        if (raw == null || !raw.matches("https://github\\.com/[A-Za-z0-9-]+/[A-Za-z0-9_.-]+/pull/\\d+#pullrequestreview-\\d+")) return null;
        return raw;
    }

    static boolean onRight(String patch, Integer target) {
        if (patch == null || target == null || target < 1) return false;
        int line = 0;
        boolean inHunk = false;
        for (String text : patch.split("\\n")) {
            Matcher header = HUNK.matcher(text);
            if (header.matches()) { line = Integer.parseInt(header.group(1)); inHunk = true; continue; }
            if (!inHunk || text.isEmpty()) continue;
            if (text.charAt(0) == ' ' || text.charAt(0) == '+') {
                if (line == target) return true;
                line++;
            }
        }
        return false;
    }

    private record Snapshot(String githubId, PullRequest pr, AnalysisRun run, List<SATDFinding> findings,
                            List<PullRequestFile> files, String fullName, String owner, String repo) {}
}
