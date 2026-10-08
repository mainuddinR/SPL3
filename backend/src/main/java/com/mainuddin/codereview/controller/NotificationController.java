package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.dto.NotificationDTO;
import com.mainuddin.codereview.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    private String githubId() { return SecurityContextHolder.getContext().getAuthentication().getName(); }

    @GetMapping
    public Page<NotificationDTO> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notifications.list(githubId(), page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() { return Map.of("count", notifications.unreadCount(githubId())); }

    @PostMapping("/{id}/read")
    public NotificationDTO markRead(@PathVariable Long id) { return notifications.markRead(githubId(), id); }

    @PostMapping("/read-all")
    public void markAllRead() { notifications.markAllRead(githubId()); }
}
