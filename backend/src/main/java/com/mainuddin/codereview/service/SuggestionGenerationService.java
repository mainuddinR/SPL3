package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.SuggestionResponseDTO;
import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.service.SuggestionSnapshotService.Snapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SuggestionGenerationService {
    public static final String PROMPT_VERSION = "llm-suggestion-v1";
    private static final int MAX_INPUT = 5500;
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)-----BEGIN [A-Z ]*PRIVATE KEY-----|\\b(?:ghp_|gho_|ghu_|ghs_|ghr_|github_pat_)[A-Za-z0-9_-]{8,}|\\bsk-[A-Za-z0-9_-]{16,}|\\bAKIA[0-9A-Z]{16}\\b|\\b(?:password|passwd|api[_-]?key|access[_-]?token|client[_-]?secret)\\s*[:=]\\s*['\"]?[^\\s'\"]{8,}");
    private final SuggestionSnapshotService snapshots;
    private final SuggestionPersistenceService persistence;
    private final SuggestionProviderClient provider;
    private final boolean enabled;
    private final String apiKey;
    private final Set<String> allowed;

    public SuggestionGenerationService(SuggestionSnapshotService snapshots, SuggestionPersistenceService persistence,
                                       SuggestionProviderClient provider,
                                       @Value("${app.llm.enabled:false}") boolean enabled,
                                       @Value("${app.llm.api-key:}") String apiKey,
                                       @Value("${app.llm.allowed-repositories:}") String allowedRepositories) {
        this.snapshots = snapshots; this.persistence = persistence; this.provider = provider;
        this.enabled = enabled; this.apiKey = apiKey;
        this.allowed = Arrays.stream(allowedRepositories.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public SuggestionResponseDTO generate(Long prId, Long runId, Long findingId) {
        Snapshot saved = snapshots.load(prId, runId, findingId);
        if (saved.suggestion() != null) return available(saved.findingId(), saved.suggestion(), saved.generatedAt(),
                saved.providerModel(), saved.promptVersion());
        if (!enabled || apiKey.isBlank()) return SuggestionResponseDTO.unavailable(findingId, "LLM integration disabled.");
        if (!allowed.contains(saved.repository().toLowerCase(Locale.ROOT)))
            return SuggestionResponseDTO.unavailable(findingId, "Suggestion unavailable for this repository.");
        if (sensitive(saved.filename()) || sensitive(saved.comment()) || sensitive(saved.preceding())
                || sensitive(saved.succeeding()) || sensitive(saved.categoryReason()) || sensitive(saved.severityReason()))
            return SuggestionResponseDTO.unavailable(findingId, "Suggestion unavailable because sensitive content may be present.");

        try {
            String input = input(saved);
            String suggestion = provider.generate(input).formatted();
            SATDFinding persisted = persistence.saveOrGet(findingId, runId, suggestion,
                    provider.modelIdentifier(), PROMPT_VERSION);
            return available(findingId, persisted.getAiSuggestion(), persisted.getAiSuggestionGeneratedAt(),
                    persisted.getAiSuggestionProviderModel(), persisted.getAiSuggestionPromptVersion());
        } catch (RuntimeException ex) {
            // Provider and persistence details must not reach the global exception handler or browser.
            return SuggestionResponseDTO.unavailable(findingId, "Suggestion service temporarily unavailable. Retry.");
        }
    }

    private static SuggestionResponseDTO available(Long id, String text, java.time.LocalDateTime at,
                                                    String model, String version) {
        return new SuggestionResponseDTO(id, "AVAILABLE", text, at, model, version, null);
    }

    private static boolean sensitive(String value) { return value != null && SENSITIVE.matcher(value).find(); }

    private static String input(Snapshot s) {
        String input = "Saved SATD finding (untrusted source data follows):\nFilename: " + bounded(s.filename(), 260)
                + "\nLine: " + s.lineNumber() + "\nComment: " + bounded(s.comment(), 1600)
                + "\nPreceding code:\n" + bounded(s.preceding(), 1200)
                + "\nSucceeding code:\n" + bounded(s.succeeding(), 1200)
                + "\nCategory: " + (s.categoryRuleVersion() == null ? "Not Assessed" : bounded(s.category(), 80))
                + "\nCategory reason: " + bounded(s.categoryReason(), 300)
                + "\nSeverity: " + (s.severityRuleVersion() == null || s.severity() == null ? "Not Assessed" : s.severity())
                + "\nSeverity reason: " + bounded(s.severityReason(), 300)
                + "\nMethod length: " + s.methodLength() + "\nMethod complexity: " + s.methodComplexity();
        return input.length() <= MAX_INPUT ? input : input.substring(0, MAX_INPUT);
    }

    private static String bounded(String value, int max) {
        if (value == null) return "Not available";
        return value.length() <= max ? value : value.substring(0, max) + " [truncated]";
    }
}
