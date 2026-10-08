package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.GithubPullRequestDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.security.GithubAuthorizedClientService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.List;

@Component
public class GithubPullRequestClient {
    private final RestTemplate http;
    private final GithubAuthorizedClientService clients;

    public GithubPullRequestClient(@Qualifier("githubSyncRestTemplate") RestTemplate http,
            GithubAuthorizedClientService clients) {
        this.http = http;
        this.clients = clients;
    }

    public boolean hasValidSession(String githubId) {
        return clients.hasValidClient(githubId);
    }

    public List<GithubPullRequestDTO> fetch(String githubId, GithubProject project) {
        OAuth2AuthorizedClient client = clients.loadValidForGithubId(githubId);
        if (client == null) {
            throw new GithubDiscoveryException(HttpStatus.UNAUTHORIZED,
                    "Your GitHub session needs to be refreshed. Sign in with GitHub again.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(client.getAccessToken().getTokenValue());
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        String url = "https://api.github.com/repos/" + project.getOwnerLogin() + "/" + project.getName()
                + "/pulls?state=all&per_page=100";
        try {
            List<GithubPullRequestDTO> result = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<GithubPullRequestDTO>>() {}).getBody();
            return result == null ? List.of() : result;
        } catch (HttpClientErrorException ex) {
            int status = ex.getStatusCode().value();
            if (status == 429 || status == 403 && ("0".equals(ex.getResponseHeaders() == null ? null
                    : ex.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
                    || ex.getResponseHeaders() != null && ex.getResponseHeaders().containsKey("Retry-After")
                    || ex.getResponseBodyAsString().toLowerCase().contains("rate limit"))) {
                throw new GithubDiscoveryException(HttpStatus.TOO_MANY_REQUESTS,
                        "GitHub request limit reached. Please wait and try again.");
            }
            if (status == 401) throw new GithubDiscoveryException(HttpStatus.UNAUTHORIZED,
                    "Your GitHub session needs to be refreshed. Sign in with GitHub again.");
            throw new GithubDiscoveryException(HttpStatus.BAD_GATEWAY,
                    "GitHub pull requests are unavailable right now.");
        } catch (RestClientException ex) {
            throw new GithubDiscoveryException(HttpStatus.BAD_GATEWAY,
                    "GitHub pull requests are unavailable right now.");
        }
    }
}
