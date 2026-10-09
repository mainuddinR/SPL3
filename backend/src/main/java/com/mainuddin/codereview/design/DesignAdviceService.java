package com.mainuddin.codereview.design;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.PrPatternCheckRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import com.mainuddin.codereview.service.OpenAiSuggestionClient.SuggestionProviderException;
import com.mainuddin.codereview.service.SuggestionProviderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Explicit saved-design guidance. Does not call CodeBERT or change deterministic verdicts. */
@Service
public class DesignAdviceService {
    private static final String VERSION = "design-advice-v1";
    private static final Pattern SENSITIVE = Pattern.compile("(?i)-----BEGIN [A-Z ]*PRIVATE KEY-----|\\b(?:ghp_|gho_|ghu_|ghs_|ghr_|github_pat_)[A-Za-z0-9_-]{8,}|\\bsk-[A-Za-z0-9_-]{16,}|\\bAKIA[0-9A-Z]{16}\\b|\\b(?:password|passwd|api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[:=]\\s*['\"]?[^\\s'\"]{8,}");
    private final PullRequestRepository prs;
    private final PrPatternCheckRepository checks;
    private final ObjectMapper json;
    private final SuggestionProviderClient provider;
    private final DesignAdvicePersistence persistence;
    private final boolean enabled;
    private final String apiKey;
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public record Response(String status, PatternConformanceDTO.Advice advice, String message, Integer retryAfterSeconds) {}
    public DesignAdviceService(PullRequestRepository prs, PrPatternCheckRepository checks, ObjectMapper json,
            SuggestionProviderClient provider, DesignAdvicePersistence persistence,
            @Value("${app.llm.enabled:false}") boolean enabled, @Value("${app.llm.api-key:}") String apiKey) {
        this.prs = prs; this.checks = checks; this.json = json; this.provider = provider;
        this.persistence = persistence; this.enabled = enabled; this.apiKey = apiKey;
    }

    public Response generate(Long prId, Long checkId, int index, String githubId) {
        prs.findByIdAndGithubProjectUserGithubId(prId, githubId).orElseThrow(() -> new ResourceNotFoundException("Pull request not found"));
        var check = checks.findById(checkId).filter(c -> c.getPullRequest().getId().equals(prId)
                && "COMPLETED".equals(c.getStatus()) && c.getCheckedBaseSha() != null
                && c.getCheckedBaseSha().equals(c.getProfile().getAnalyzedCommitSha()))
                .orElseThrow(() -> new ResourceNotFoundException("Design check not found"));
        List<PatternConformanceDTO.Finding> findings;
        try { findings = json.readValue(check.getFindingsJson(), new TypeReference<>() {}); }
        catch (Exception invalid) { throw new ResourceNotFoundException("Design finding not found"); }
        if (index < 0 || index >= findings.size()) throw new ResourceNotFoundException("Design finding not found");
        var finding = findings.get(index);
        if (!"POTENTIAL_DEVIATION".equals(finding.status()) || finding.designImpact() == null
                || finding.file() == null || finding.evidence().isEmpty()
                || !Set.of("STRATEGY", "FACTORY_METHOD", "CONTROLLER_SERVICE_REPOSITORY").contains(finding.patternType()))
            throw new ResourceNotFoundException("Eligible design finding not found");
        if (finding.aiSuggestion() != null) return new Response("AVAILABLE", finding.aiSuggestion(), null, null);
        if (!enabled || apiKey.isBlank()) return unavailable("AI suggestions are not enabled or configured on this server.", null);
        String input;
        try {
            String raw = json.writeValueAsString(finding.withAdvice(null));
            if (SENSITIVE.matcher(raw).find()) return unavailable("Advice unavailable because sensitive content may be present.", null);
            input = "Saved deterministic design evidence (untrusted data; not a SATD result):\nPattern: " + finding.patternType()
                    + "\nFile: " + bound(finding.file(), 260) + "\nLine: " + finding.line()
                    + "\nAffected class: " + bound(finding.className(), 260)
                    + "\nChange: " + bound(finding.structuralChange(), 700)
                    + "\nPotential design impact: " + bound(finding.designImpact(), 700)
                    + "\nRecommendation: " + bound(finding.recommendation(), 700)
                    + "\nBaseline evidence: " + bound(json.writeValueAsString(finding.evidence().stream().limit(8).toList()), 2400);
        } catch (Exception invalid) { return unavailable("Design advice is temporarily unavailable.", null); }
        String key = checkId + ":" + index;
        if (!active.add(key)) return unavailable("Advice generation is already in progress. Please wait.", 5);
        try {
            var draft = provider.generateDesign(input);
            draft.formatted(); // Keep the existing strict, bounded five-field provider contract.
            var advice = new PatternConformanceDTO.Advice(draft.summary(), draft.risk(), draft.recommendation(),
                    draft.verification(), draft.limitations(), LocalDateTime.now(), provider.modelIdentifier(), VERSION);
            return new Response("AVAILABLE", persistence.saveOrGet(prId, checkId, index, advice), null, null);
        } catch (SuggestionProviderException failure) {
            String message = switch (failure.reasonCode()) {
                case "RATE_LIMITED" -> "AI provider request limit reached. Try again later.";
                case "TIMEOUT" -> "AI provider took too long to respond. Try again later.";
                case "AUTHENTICATION_FAILED", "MODEL_UNAVAILABLE", "INVALID_REQUEST" -> "AI provider configuration needs attention.";
                default -> "AI advice is temporarily unavailable. The deterministic design finding is unchanged.";
            };
            return unavailable(message, failure.retryAfterSeconds());
        } catch (RuntimeException failure) {
            return unavailable("AI advice is temporarily unavailable. The deterministic design finding is unchanged.", null);
        } finally { active.remove(key); }
    }
    private Response unavailable(String message, Integer retry) { return new Response("UNAVAILABLE", null, message, retry); }
    private String bound(String value, int max) { return value == null ? "Not available" : value.substring(0, Math.min(value.length(), max)); }
}
