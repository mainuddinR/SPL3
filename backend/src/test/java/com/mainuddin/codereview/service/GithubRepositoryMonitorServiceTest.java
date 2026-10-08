package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.User;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubRepositoryMonitorServiceTest {
    private final GithubProjectRepository projects = mock(GithubProjectRepository.class);
    private final GithubService sync = mock(GithubService.class);
    private final GithubPullRequestClient client = mock(GithubPullRequestClient.class);

    private GithubProject project(long id, String githubId) {
        return GithubProject.builder().id(id).fullName("owner/repo-" + id)
                .user(User.builder().githubId(githubId).build()).build();
    }

    @Test void scansOnlyStoredRepositoriesForTheirOwnUsers() {
        when(projects.findAllWithUser()).thenReturn(List.of(project(1, "user-a"), project(2, "user-b")));
        when(client.hasValidSession("user-a")).thenReturn(true);
        when(client.hasValidSession("user-b")).thenReturn(true);
        new GithubRepositoryMonitorService(projects, sync, client, true).scan();
        verify(sync).fetchAndStorePullRequests(1L, "user-a");
        verify(sync).fetchAndStorePullRequests(2L, "user-b");
        verifyNoMoreInteractions(sync);
    }

    @Test void missingSessionSkipsThatUsersRepositoriesWithoutAnonymousFallback() {
        when(projects.findAllWithUser()).thenReturn(List.of(project(1, "user-a"), project(2, "user-a"), project(3, "user-b")));
        when(client.hasValidSession("user-b")).thenReturn(true);
        new GithubRepositoryMonitorService(projects, sync, client, true).scan();
        verify(client, times(1)).hasValidSession("user-a");
        verify(sync).fetchAndStorePullRequests(3L, "user-b");
        verifyNoMoreInteractions(sync);
    }

    @Test void oneProviderFailureDoesNotStopOtherRepositories() {
        when(projects.findAllWithUser()).thenReturn(List.of(project(1, "user-a"), project(2, "user-a")));
        when(client.hasValidSession("user-a")).thenReturn(true);
        doThrow(new GithubDiscoveryException(HttpStatus.BAD_GATEWAY, "unavailable"))
                .when(sync).fetchAndStorePullRequests(1L, "user-a");
        new GithubRepositoryMonitorService(projects, sync, client, true).scan();
        verify(sync).fetchAndStorePullRequests(2L, "user-a");
    }

    @Test void rateLimitSkipsRemainingUserProjectsAndBacksOffLaterScan() {
        when(projects.findAllWithUser()).thenReturn(List.of(project(1, "user-a"), project(2, "user-a"), project(3, "user-b")));
        when(client.hasValidSession("user-a")).thenReturn(true);
        when(client.hasValidSession("user-b")).thenReturn(true);
        doThrow(new GithubDiscoveryException(HttpStatus.TOO_MANY_REQUESTS, "limited"))
                .when(sync).fetchAndStorePullRequests(1L, "user-a");
        var monitor = new GithubRepositoryMonitorService(projects, sync, client, true);
        monitor.scan(); monitor.scan();
        verify(sync, times(1)).fetchAndStorePullRequests(1L, "user-a");
        verify(sync, never()).fetchAndStorePullRequests(2L, "user-a");
        verify(sync, times(2)).fetchAndStorePullRequests(3L, "user-b");
    }

    @Test void disabledMonitorDoesNotEnumerateOrCallGithub() {
        new GithubRepositoryMonitorService(projects, sync, client, false).scan();
        verifyNoInteractions(projects, sync, client);
    }

    @Test void overlappingScanDoesNotStartSecondPass() throws Exception {
        when(projects.findAllWithUser()).thenReturn(List.of(project(1, "user-a")));
        when(client.hasValidSession("user-a")).thenReturn(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); release.await(3, TimeUnit.SECONDS); return null; })
                .when(sync).fetchAndStorePullRequests(1L, "user-a");
        var monitor = new GithubRepositoryMonitorService(projects, sync, client, true);
        Thread first = new Thread(monitor::scan);
        first.start();
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            monitor.scan();
        } finally {
            release.countDown();
            first.join(3000);
        }
        verify(projects, times(1)).findAllWithUser();
        verify(sync, times(1)).fetchAndStorePullRequests(1L, "user-a");
    }
}
