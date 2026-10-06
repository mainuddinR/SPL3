package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.UiCapabilityDTO;
import com.mainuddin.codereview.dto.UiStatusDTO;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

@Service
public class UiStatusService {
    private final UserRepository users;
    private final GithubProjectRepository projects;
    private final OAuth2AuthorizedClientService clients;
    private final DataSource dataSource;
    private final RestTemplate probe;
    private final String mlUrl;
    private final boolean llmEnabled;
    private final boolean keyConfigured;
    private final String model;

    public UiStatusService(UserRepository users, GithubProjectRepository projects,
            OAuth2AuthorizedClientService clients, DataSource dataSource,
            @Value("${app.ml-service.url}") String mlUrl,
            @Value("${app.llm.enabled:false}") boolean llmEnabled,
            @Value("${app.llm.api-key:}") String key,
            @Value("${app.llm.model:gpt-5-mini}") String model) {
        this.users = users; this.projects = projects; this.clients = clients; this.dataSource = dataSource;
        this.mlUrl = mlUrl; this.llmEnabled = llmEnabled; this.keyConfigured = !key.isBlank(); this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500); factory.setReadTimeout(1500);
        this.probe = new RestTemplate(factory);
    }

    public UiStatusDTO status(String githubId) {
        var user = users.findByGithubId(githubId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        OAuth2AuthorizedClient client = clients.loadAuthorizedClient("github", githubId);
        boolean githubConnected = client != null && client.getAccessToken() != null
                && (client.getAccessToken().getExpiresAt() == null
                    || client.getAccessToken().getExpiresAt().isAfter(Instant.now()));
        boolean databaseConnected;
        try (Connection connection = dataSource.getConnection()) {
            databaseConnected = connection.isValid(2);
        } catch (Exception ignored) { databaseConnected = false; }
        boolean mlAvailable;
        try {
            mlAvailable = probe.getForEntity(mlUrl + "/docs", String.class).getStatusCode().is2xxSuccessful();
        } catch (Exception ignored) { mlAvailable = false; }
        return new UiStatusDTO(user.getGithubId(), user.getUsername(), user.getAvatarUrl(),
                projects.countByUserGithubId(githubId), githubConnected, mlAvailable,
                databaseConnected, new UiCapabilityDTO(llmEnabled, keyConfigured, model));
    }
}
