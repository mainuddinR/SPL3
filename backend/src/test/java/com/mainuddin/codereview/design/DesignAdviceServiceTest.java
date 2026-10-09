package com.mainuddin.codereview.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.*;
import com.mainuddin.codereview.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DesignAdviceServiceTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final PullRequestRepository prs = mock(PullRequestRepository.class);
    private final PrPatternCheckRepository checks = mock(PrPatternCheckRepository.class);
    private final SuggestionProviderClient provider = mock(SuggestionProviderClient.class);
    private final DesignAdvicePersistence persistence = mock(DesignAdvicePersistence.class);
    private DesignAdviceService service;
    private PrPatternCheck check;
    private PatternConformanceDTO.Finding finding;
    @BeforeEach void setup() throws Exception {
        var pr = PullRequest.builder().id(1L).build();
        var profile = new RepositoryPatternProfile(); profile.setAnalyzedCommitSha("a".repeat(40));
        check = new PrPatternCheck(); check.setId(2L); check.setPullRequest(pr); check.setProfile(profile);
        check.setStatus("COMPLETED"); check.setCheckedBaseSha("a".repeat(40));
        finding = new PatternConformanceDTO.Finding("CONTROLLER_SERVICE_REPOSITORY", "ARCHITECTURAL", "POTENTIAL_DEVIATION", "HIGH",
                "Controller.java", 5, "Controller", "New direct repository field", "Use the service boundary",
                List.of(new PatternCandidate.Evidence("Repository", "Store", "Store.java", null, 1)),
                "New repository dependency", "HTTP boundary is coupled to persistence", "HIGH", null);
        check.setFindingsJson(json.writeValueAsString(List.of(finding)));
        when(prs.findByIdAndGithubProjectUserGithubId(1L, "owner")).thenReturn(Optional.of(pr));
        when(checks.findById(2L)).thenReturn(Optional.of(check));
        service = new DesignAdviceService(prs, checks, json, provider, persistence, true, "unit-test-key");
    }
    @Test void providerFailureRetainsVerdictAndSavedEvidence() {
        String before = check.getFindingsJson();
        when(provider.generateDesign(anyString())).thenThrow(new OpenAiSuggestionClient.SuggestionProviderException("RATE_LIMITED", 60));
        var result = service.generate(1L, 2L, 0, "owner");
        assertEquals("UNAVAILABLE", result.status()); assertEquals(60, result.retryAfterSeconds());
        assertEquals(before, check.getFindingsJson()); assertEquals("COMPLETED", check.getStatus());
        verifyNoInteractions(persistence); verify(provider, never()).generate(anyString());
    }
    @Test void boundedDesignOnlyEvidenceGeneratesSavedAdviceWithoutSatdInference() {
        when(provider.generateDesign(anyString())).thenReturn(new SuggestionDraft("Summary", "Coupling", "Use service", "Test routing", "Partial context"));
        when(provider.modelIdentifier()).thenReturn("configured-provider-model");
        when(persistence.saveOrGet(eq(1L), eq(2L), eq(0), any())).thenAnswer(c -> c.getArgument(3));
        var result = service.generate(1L, 2L, 0, "owner");
        assertEquals("AVAILABLE", result.status()); assertEquals("design-advice-v1", result.advice().promptVersion());
        var input = org.mockito.ArgumentCaptor.forClass(String.class); verify(provider).generateDesign(input.capture());
        assertTrue(input.getValue().length() <= 5500); assertTrue(input.getValue().contains("not a SATD result"));
        assertEquals("POTENTIAL_DEVIATION", finding.status());
    }
    @Test void wrongOwnerCheckRelationshipOrNonDeviationCannotCallProvider() throws Exception {
        assertThrows(ResourceNotFoundException.class, () -> service.generate(1L, 2L, 0, "other"));
        assertThrows(ResourceNotFoundException.class, () -> service.generate(1L, 2L, -1, "owner"));
        check.setCheckedBaseSha("b".repeat(40));
        assertThrows(ResourceNotFoundException.class, () -> service.generate(1L, 2L, 0, "owner"));
        check.setCheckedBaseSha("a".repeat(40));
        check.setFindingsJson(json.writeValueAsString(List.of(new PatternConformanceDTO.Finding("STRATEGY", "GOF", "CONFORMS", "HIGH",
                "Strategy.java", 1, "Strategy", "Preserved", null, finding.evidence()))));
        assertThrows(ResourceNotFoundException.class, () -> service.generate(1L, 2L, 0, "owner"));
        verifyNoInteractions(provider, persistence);
    }
    @Test void storedAdviceIsReturnedWithoutAnotherProviderRequest() throws Exception {
        var saved = new PatternConformanceDTO.Advice("Summary", "Risk", "Recommend", "Verify", "Review",
                java.time.LocalDateTime.now(), "model", "design-advice-v1");
        check.setFindingsJson(json.writeValueAsString(List.of(finding.withAdvice(saved))));
        assertEquals(saved, service.generate(1L, 2L, 0, "owner").advice());
        verifyNoInteractions(provider, persistence);
    }
    @Test void sensitiveEvidenceIsBlockedBeforeProvider() throws Exception {
        var sensitive = new PatternConformanceDTO.Finding(finding.patternType(), finding.patternFamily(), finding.status(), "HIGH",
                "Controller.java", 1, "Controller", "password=verysecretvalue", "Use service", finding.evidence(), "Changed", "Impact", "HIGH", null);
        check.setFindingsJson(json.writeValueAsString(List.of(sensitive)));
        assertEquals("UNAVAILABLE", service.generate(1L, 2L, 0, "owner").status());
        verifyNoInteractions(provider, persistence);
    }
}
