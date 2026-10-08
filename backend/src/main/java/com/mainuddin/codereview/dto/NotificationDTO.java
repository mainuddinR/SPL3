package com.mainuddin.codereview.dto;

import com.mainuddin.codereview.entity.AppNotification;
import java.time.LocalDateTime;

public record NotificationDTO(Long id, String type, String repository, Long pullRequestId,
        Integer pullRequestNumber, String title, String message, boolean read, LocalDateTime createdAt) {
    public static NotificationDTO from(AppNotification notification) {
        return new NotificationDTO(notification.getId(), notification.getType(),
                notification.getGithubProject().getFullName(), notification.getPullRequest().getId(),
                notification.getPullRequest().getNumber(), notification.getTitle(),
                notification.getMessage(), notification.isReadFlag(), notification.getCreatedAt());
    }
}
