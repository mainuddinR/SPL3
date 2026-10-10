package com.mainuddin.codereview.security;

import com.mainuddin.codereview.controller.PullRequestController;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.*;
import com.mainuddin.codereview.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyPrOwnerAccessTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void nonOwnerCannotReadOrSynchronizeOtherUsersPr() {
        var prs = mock(PullRequestRepository.class);
        var files = mock(PullRequestFileRepository.class);
        var comments = mock(PullRequestCommentRepository.class);
        var github = mock(GithubService.class);
        var controller = new PullRequestController(
                github, prs, files, comments, mock(PullRequestAnalysisService.class),
                mock(AnalysisHistoryService.class), mock(SuggestionGenerationService.class),
                mock(ReviewPublishingService.class), mock(ProgressiveAnalysisService.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("another-user", null));
        when(prs.findByIdAndGithubProjectUserGithubId(42L,"another-user"))
                .thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getPullRequest(42L).getStatusCode());
        assertThrows(ResourceNotFoundException.class, () -> controller.getPullRequestFiles(42L));
        assertThrows(ResourceNotFoundException.class, () -> controller.getPullRequestComments(42L));
        assertThrows(ResourceNotFoundException.class, () -> controller.syncPullRequestDetails(42L));
        verifyNoInteractions(files, comments, github);
    }

    @Test void ownerCanReadSavedPrDetails() {
        var prs = mock(PullRequestRepository.class);
        var files = mock(PullRequestFileRepository.class);
        var comments = mock(PullRequestCommentRepository.class);
        var controller = new PullRequestController(
                mock(GithubService.class), prs, files, comments,
                mock(PullRequestAnalysisService.class), mock(AnalysisHistoryService.class),
                mock(SuggestionGenerationService.class), mock(ReviewPublishingService.class),
                mock(ProgressiveAnalysisService.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner", null));
        var pr = new PullRequest();
        pr.setId(42L);
        when(prs.findByIdAndGithubProjectUserGithubId(42L,"owner"))
                .thenReturn(Optional.of(pr));

        assertEquals(HttpStatus.OK, controller.getPullRequest(42L).getStatusCode());
        assertEquals(HttpStatus.OK, controller.getPullRequestFiles(42L).getStatusCode());
        assertEquals(HttpStatus.OK, controller.getPullRequestComments(42L).getStatusCode());
        verify(files).findByPullRequestId(42L);
        verify(comments).findByPullRequestId(42L);
    }
}
