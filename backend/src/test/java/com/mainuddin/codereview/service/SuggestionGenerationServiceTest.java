package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.service.SuggestionSnapshotService.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SuggestionGenerationServiceTest {
    private final SuggestionSnapshotService snapshots = mock(SuggestionSnapshotService.class);
    private final SuggestionPersistenceService persistence = mock(SuggestionPersistenceService.class);
    private final SuggestionProviderClient provider = mock(SuggestionProviderClient.class);

    private Snapshot snapshot(String repository, String comment, String saved) {
        return new Snapshot(repository, 3L, 2L, "Main.java", 5, comment, "void method() {", "}",
                "DESIGN", "category-v1", "Design debt", 3, "severity-v1", "Long method", 40, 5,
                saved, saved == null ? null : LocalDateTime.now(), saved == null ? null : "old-model",
                saved == null ? null : "old-version");
    }

    private SuggestionGenerationService service(boolean enabled, String key, String allowed) {
        return new SuggestionGenerationService(snapshots, persistence, provider, enabled, key, allowed);
    }

    @Test
    void successUsesSavedEvidenceAndPersistsFormattedSnapshot() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO simplify", null));
        when(provider.generate(anyString())).thenReturn(new SuggestionDraft("Summary", "Risk", "Review the method", "Run tests", "More context needed"));
        when(provider.modelIdentifier()).thenReturn("test-model");
        when(persistence.saveOrGet(eq(3L), eq(2L), contains("Recommendation\nReview the method"), eq("test-model"), eq("llm-suggestion-v1")))
                .thenReturn(SATDFinding.builder().id(3L).aiSuggestion("Summary\nSummary").aiSuggestionProviderModel("test-model")
                        .aiSuggestionPromptVersion("llm-suggestion-v1").build());
        var result = service(true, "test-key", "owner/repo").generate(1L, 2L, 3L);
        assertEquals("AVAILABLE", result.status());
        assertEquals("Summary\nSummary", result.suggestion());
        verify(provider).generate(argThat(input -> input.contains("// TODO simplify") && input.length() <= 5500));
    }

    @Test
    void existingSuggestionNeverInvokesProvider() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", "Saved advice"));
        assertEquals("Saved advice", service(false, "", "").generate(1L, 2L, 3L).suggestion());
        verifyNoInteractions(provider, persistence);
    }

    @Test
    void disabledMissingKeyUnlistedAndSecretBlockTransmission() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", null));
        assertEquals("UNAVAILABLE", service(false, "key", "owner/repo").generate(1L, 2L, 3L).status());
        assertEquals("UNAVAILABLE", service(true, "", "owner/repo").generate(1L, 2L, 3L).status());
        assertEquals("UNAVAILABLE", service(true, "key", "other/repo").generate(1L, 2L, 3L).status());
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "password = verySecret123", null));
        assertEquals("UNAVAILABLE", service(true, "key", "owner/repo").generate(1L, 2L, 3L).status());
        verifyNoInteractions(provider, persistence);
    }

    @Test
    void providerErrorsAreSafeAndDoNotPersist() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", null));
        when(provider.generate(anyString())).thenThrow(new RuntimeException("raw secret provider body"));
        var result = service(true, "key", "owner/repo").generate(1L, 2L, 3L);
        assertEquals("UNAVAILABLE", result.status());
        assertFalse(result.reason().contains("raw secret"));
        verifyNoInteractions(persistence);
    }

    @Test
    void invalidStructuredOutputIsRejected() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", null));
        when(provider.generate(anyString())).thenReturn(new SuggestionDraft("Summary", "Risk", " ", "Verify", "Limits"));
        assertEquals("UNAVAILABLE", service(true, "key", "owner/repo").generate(1L, 2L, 3L).status());
        verifyNoInteractions(persistence);
    }
}
