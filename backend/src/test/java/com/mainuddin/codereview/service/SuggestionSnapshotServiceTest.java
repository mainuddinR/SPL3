package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuggestionSnapshotServiceTest {
    private final PullRequestRepository prs = mock(PullRequestRepository.class);
    private final AnalysisRunRepository runs = mock(AnalysisRunRepository.class);
    private final SATDFindingRepository findings = mock(SATDFindingRepository.class);
    private final SuggestionSnapshotService service = new SuggestionSnapshotService(prs, runs, findings);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner-id", "", java.util.List.of()));
    }

    @Test void ownerCompletedRunAndMatchingFindingAreRequired() {
        authenticate();
        assertThrows(ResourceNotFoundException.class, () -> service.load(1L, 2L, 3L));
        verifyNoInteractions(runs, findings);

        PullRequest pr = PullRequest.builder().id(1L)
                .githubProject(GithubProject.builder().fullName("owner/repo").build()).build();
        when(prs.findByIdAndGithubProjectUserGithubId(1L, "owner-id")).thenReturn(Optional.of(pr));
        assertThrows(ResourceNotFoundException.class, () -> service.load(1L, 2L, 3L));
        verifyNoInteractions(findings);

        when(runs.findByIdAndPullRequestId(2L, 1L)).thenReturn(Optional.of(
                AnalysisRun.builder().id(2L).status(AnalysisRunStatus.RUNNING).build()));
        assertThrows(ResourceNotFoundException.class, () -> service.load(1L, 2L, 3L));
        verifyNoInteractions(findings);

        when(runs.findByIdAndPullRequestId(2L, 1L)).thenReturn(Optional.of(
                AnalysisRun.builder().id(2L).status(AnalysisRunStatus.COMPLETED).build()));
        assertThrows(ResourceNotFoundException.class, () -> service.load(1L, 2L, 3L));
        when(findings.findByIdAndAnalysisRunId(3L, 2L)).thenReturn(Optional.of(
                SATDFinding.builder().id(3L).filename("Main.java").commentText("// TODO").build()));
        assertEquals("owner/repo", service.load(1L, 2L, 3L).repository());
    }
}
