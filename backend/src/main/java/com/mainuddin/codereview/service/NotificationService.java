package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.NotificationDTO;
import com.mainuddin.codereview.entity.AppNotification;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.repository.AppNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service
public class NotificationService {
    private final AppNotificationRepository repository;
    public NotificationService(AppNotificationRepository repository) { this.repository = repository; }

    @Transactional
    public void newPullRequest(GithubProject project, PullRequest pullRequest) {
        String key = "NEW_PR:" + project.getId() + ":" + pullRequest.getGithubPrId();
        if (repository.existsByDedupKey(key)) return;
        AppNotification notification = new AppNotification();
        notification.setUser(project.getUser());
        notification.setGithubProject(project);
        notification.setPullRequest(pullRequest);
        notification.setType("NEW_PR");
        notification.setTitle("New pull request #" + pullRequest.getNumber());
        notification.setMessage(pullRequest.getTitle().length() > 255 ? pullRequest.getTitle().substring(0, 255) : pullRequest.getTitle());
        notification.setDedupKey(key);
        notification.setCreatedAt(LocalDateTime.now());
        repository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> list(String githubId, int page, int size, boolean unreadOnly) {
        PageRequest request = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)));
        return (unreadOnly
                ? repository.findByUserGithubIdAndReadFlagFalseOrderByCreatedAtDescIdDesc(githubId, request)
                : repository.findByUserGithubIdOrderByCreatedAtDescIdDesc(githubId, request)).map(NotificationDTO::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String githubId) { return repository.countByUserGithubIdAndReadFlagFalse(githubId); }

    @Transactional
    public NotificationDTO markRead(String githubId, Long id) {
        AppNotification notification = repository.findByIdAndUserGithubId(id, githubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isReadFlag()) {
            notification.setReadFlag(true);
            notification.setReadAt(LocalDateTime.now());
        }
        return NotificationDTO.from(notification);
    }

    @Transactional
    public void markAllRead(String githubId) {
        LocalDateTime now = LocalDateTime.now();
        for (AppNotification notification : repository.findByUserGithubIdAndReadFlagFalse(githubId)) {
            notification.setReadFlag(true);
            notification.setReadAt(now);
        }
    }
}
