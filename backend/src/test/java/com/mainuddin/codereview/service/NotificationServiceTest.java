package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.AppNotification;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.User;
import com.mainuddin.codereview.repository.AppNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final AppNotificationRepository repository = mock(AppNotificationRepository.class);
    private final NotificationService service = new NotificationService(repository);

    @Test void createsOwnerScopedNotificationWithStableKeyAndSkipsDuplicate() {
        User owner = User.builder().githubId("owner").build();
        GithubProject project = GithubProject.builder().id(3L).fullName("owner/repo").user(owner).build();
        PullRequest pr = PullRequest.builder().githubPrId(71L).number(4).title("Change").build();
        service.newPullRequest(project, pr);
        verify(repository).save(argThat(n -> n.getUser() == owner && n.getGithubProject() == project
                && n.getPullRequest() == pr && n.getDedupKey().equals("NEW_PR:3:71")));
        when(repository.existsByDedupKey("NEW_PR:3:71")).thenReturn(true);
        service.newPullRequest(project, pr);
        verify(repository, times(1)).save(any());
    }

    @Test void readOperationsAreScopedAndIdempotent() {
        when(repository.findByIdAndUserGithubId(1L, "other")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.markRead("other", 1L));
        AppNotification notification = new AppNotification();
        notification.setPullRequest(PullRequest.builder().id(5L).number(7).build());
        notification.setGithubProject(GithubProject.builder().fullName("owner/repo").build());
        when(repository.findByIdAndUserGithubId(1L, "owner")).thenReturn(Optional.of(notification));
        service.markRead("owner", 1L);
        var firstReadAt = notification.getReadAt();
        assertTrue(notification.isReadFlag());
        service.markRead("owner", 1L);
        assertEquals(firstReadAt, notification.getReadAt());
        verify(repository, never()).findByIdAndUserGithubId(1L, "someone-else");
    }

    @Test void unreadCountFilterPaginationAndMarkAllUseOnlyPrincipal() {
        when(repository.countByUserGithubIdAndReadFlagFalse("owner")).thenReturn(2L);
        assertEquals(2L, service.unreadCount("owner"));
        when(repository.findByUserGithubIdAndReadFlagFalseOrderByCreatedAtDescIdDesc(eq("owner"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        assertTrue(service.list("owner", 0, 10, true).isEmpty());
        verify(repository).findByUserGithubIdAndReadFlagFalseOrderByCreatedAtDescIdDesc("owner", PageRequest.of(0, 10));
        AppNotification notification = new AppNotification();
        when(repository.findByUserGithubIdAndReadFlagFalse("owner")).thenReturn(List.of(notification));
        service.markAllRead("owner");
        assertTrue(notification.isReadFlag());
        assertNotNull(notification.getReadAt());
    }
}
