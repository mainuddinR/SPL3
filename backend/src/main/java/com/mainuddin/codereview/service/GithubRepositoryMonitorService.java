package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GithubRepositoryMonitorService {
    private static final Logger log = LoggerFactory.getLogger(GithubRepositoryMonitorService.class);
    private static final long RATE_LIMIT_BACKOFF_SECONDS = 600;
    private final GithubProjectRepository projects;
    private final GithubService githubService;
    private final GithubPullRequestClient pullRequestClient;
    private final boolean enabled;
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final Map<String, Instant> rateLimitedUntil = new HashMap<>();

    public GithubRepositoryMonitorService(GithubProjectRepository projects, GithubService githubService,
            GithubPullRequestClient pullRequestClient,
            @Value("${app.github-monitor.enabled:true}") boolean enabled) {
        this.projects = projects;
        this.githubService = githubService;
        this.pullRequestClient = pullRequestClient;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.github-monitor.interval-ms:120000}",
            initialDelayString = "${app.github-monitor.initial-delay-ms:30000}")
    public void scan() {
        if (!enabled || !scanning.compareAndSet(false, true)) return;
        try {
            Set<String> unavailable = new HashSet<>();
            Set<String> valid = new HashSet<>();
            for (GithubProject project : projects.findAllWithUser()) {
                String githubId = project.getUser().getGithubId();
                if (unavailable.contains(githubId) || Instant.now().isBefore(
                        rateLimitedUntil.getOrDefault(githubId, Instant.EPOCH))) continue;
                try {
                    if (!valid.contains(githubId)) {
                        if (!pullRequestClient.hasValidSession(githubId)) {
                            unavailable.add(githubId);
                            log.info("GitHub monitoring skipped for a user requiring re-login");
                            continue;
                        }
                        valid.add(githubId);
                    }
                    githubService.fetchAndStorePullRequests(project.getId(), githubId);
                } catch (GithubDiscoveryException ex) {
                    if (ex.status() == HttpStatus.TOO_MANY_REQUESTS) {
                        rateLimitedUntil.put(githubId, Instant.now().plusSeconds(RATE_LIMIT_BACKOFF_SECONDS));
                        unavailable.add(githubId);
                        log.warn("GitHub monitoring paused for a rate-limited user");
                    } else if (ex.status() == HttpStatus.UNAUTHORIZED) {
                        unavailable.add(githubId);
                        log.info("GitHub monitoring skipped for a user requiring re-login");
                    } else {
                        log.warn("GitHub monitoring could not check repository {}", project.getFullName());
                    }
                } catch (Exception ex) {
                    if (!valid.contains(githubId)) unavailable.add(githubId);
                    log.warn("GitHub monitoring could not check repository {}", project.getFullName());
                }
            }
        } catch (Exception ex) {
            log.warn("GitHub monitoring could not enumerate stored repositories");
        } finally {
            scanning.set(false);
        }
    }
}
