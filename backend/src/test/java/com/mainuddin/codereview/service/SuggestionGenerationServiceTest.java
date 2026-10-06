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

    private SuggestionGenerationService service(boolean enabled, String key) {
        return new SuggestionGenerationService(snapshots, persistence, provider, enabled, key);
    }

    @Test
    void successUsesSavedEvidenceAndPersistsFormattedSnapshot() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO simplify", null));
        when(provider.generate(anyString())).thenReturn(new SuggestionDraft("Summary", "Risk", "Review the method", "Run tests", "More context needed"));
        when(provider.modelIdentifier()).thenReturn("test-model");
        when(persistence.saveOrGet(eq(3L), eq(2L), contains("Recommendation\nReview the method"), eq("test-model"), eq("llm-suggestion-v1")))
                .thenReturn(SATDFinding.builder().id(3L).aiSuggestion("Summary\nSummary").aiSuggestionProviderModel("test-model")
                        .aiSuggestionPromptVersion("llm-suggestion-v1").build());
        var result = service(true, "test-key").generate(1L, 2L, 3L);
        assertEquals("AVAILABLE", result.status());
        assertEquals("Summary\nSummary", result.suggestion());
        verify(provider).generate(argThat(input -> input.contains("// TODO simplify") && input.length() <= 5500));
    }

    @Test
    void twoConnectedRepositoriesUseTheSameEnabledServiceWithoutAnAllowlist() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo-a", "// TODO A", null));
        when(snapshots.load(4L, 5L, 6L)).thenReturn(new Snapshot("owner/repo-b", 6L, 5L,
                "Other.java", 7, "// TODO B", "before", "after", "DESIGN", "category-v1",
                "Design debt", 2, "severity-v1", "Reason", 10, 2, null, null, null, null));
        when(provider.generate(anyString())).thenReturn(new SuggestionDraft("Summary", "Risk", "Review code", "Run tests", "More context needed"));
        when(provider.modelIdentifier()).thenReturn("test-model");
        when(persistence.saveOrGet(eq(3L), eq(2L), anyString(), anyString(), anyString()))
                .thenReturn(SATDFinding.builder().aiSuggestion("Saved A").build());
        when(persistence.saveOrGet(eq(6L), eq(5L), anyString(), anyString(), anyString()))
                .thenReturn(SATDFinding.builder().aiSuggestion("Saved B").build());
        SuggestionGenerationService enabled = service(true, "test-key");
        assertEquals("Saved A", enabled.generate(1L, 2L, 3L).suggestion());
        assertEquals("Saved B", enabled.generate(4L, 5L, 6L).suggestion());
        verify(provider).generate(contains("// TODO A"));
        verify(provider).generate(contains("// TODO B"));
    }

    @Test
    void existingSuggestionNeverInvokesProvider() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", "Saved advice"));
        assertEquals("Saved advice", service(false, "").generate(1L, 2L, 3L).suggestion());
        verifyNoInteractions(provider, persistence);
    }

    @Test
    void disabledMissingKeyAndSecretBlockTransmission() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", null));
        assertEquals("UNAVAILABLE", service(false, "key").generate(1L, 2L, 3L).status());
        assertEquals("UNAVAILABLE", service(true, "").generate(1L, 2L, 3L).status());
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "password = verySecret123", null));
        assertEquals("UNAVAILABLE", service(true, "key").generate(1L, 2L, 3L).status());
        verifyNoInteractions(provider, persistence);
    }

    @Test
    void providerErrorsAreSafeAndDoNotPersist() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", null));
        when(provider.generate(anyString())).thenThrow(new RuntimeException("raw secret provider body"));
        var result = service(true, "key").generate(1L, 2L, 3L);
        assertEquals("UNAVAILABLE", result.status());
        assertFalse(result.reason().contains("raw secret"));
        verifyNoInteractions(persistence);
    }

    @Test
    void providerCategoriesReachApiWithoutRawProviderDetails() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", null));
        for (String code : new String[] {"AUTHENTICATION_FAILED", "RATE_LIMITED", "MODEL_UNAVAILABLE",
                "TIMEOUT", "INVALID_PROVIDER_RESPONSE"}) {
            doThrow(new OpenAiSuggestionClient.SuggestionProviderException(code)).when(provider).generate(anyString());
            var result = service(true, "key").generate(1L, 2L, 3L);
            assertEquals("UNAVAILABLE", result.status());
            assertEquals(code, result.reasonCode());
            assertNull(result.suggestion());
            assertFalse(result.reason().contains("private"));
        }
        verifyNoInteractions(persistence);
    }

    @Test
    void invalidStructuredOutputIsRejected() {
        when(snapshots.load(1L, 2L, 3L)).thenReturn(snapshot("owner/repo", "// TODO", null));
        when(provider.generate(anyString())).thenReturn(new SuggestionDraft("Summary", "Risk", " ", "Verify", "Limits"));
        assertEquals("UNAVAILABLE", service(true, "key").generate(1L, 2L, 3L).status());
        verifyNoInteractions(persistence);
    }
}
