package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.AppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    boolean existsByDedupKey(String dedupKey);
    Page<AppNotification> findByUserGithubIdOrderByCreatedAtDescIdDesc(String githubId, Pageable pageable);
    Page<AppNotification> findByUserGithubIdAndReadFlagFalseOrderByCreatedAtDescIdDesc(String githubId, Pageable pageable);
    long countByUserGithubIdAndReadFlagFalse(String githubId);
    Optional<AppNotification> findByIdAndUserGithubId(Long id, String githubId);
    java.util.List<AppNotification> findByUserGithubIdAndReadFlagFalse(String githubId);
}
