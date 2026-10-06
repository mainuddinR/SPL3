package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.GithubRepoDTO;
import com.mainuddin.codereview.dto.RepositoryPageDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GithubDiscoveryService {
    private static final int PAGE_SIZE = 50;
    private final RestTemplate http;
    private final OAuth2AuthorizedClientService authorizedClients;
    private final GithubProjectRepository projects;
    private final GithubService githubService;

    public GithubDiscoveryService(RestTemplate http, OAuth2AuthorizedClientService authorizedClients,
                                  GithubProjectRepository projects, GithubService githubService) {
        this.http = http; this.authorizedClients = authorizedClients;
        this.projects = projects; this.githubService = githubService;
    }

    public RepositoryPageDTO mine(String githubId, int page) {
        return page(githubId, "https://api.github.com/user/repos?per_page=" + PAGE_SIZE
                + "&page=" + checkedPage(page) + "&sort=updated", page);
    }

    public RepositoryPageDTO byUser(String githubId, String username, int page) {
        String name = checkedUser(username);
        request(githubId, "https://api.github.com/users/" + name, new ParameterizedTypeReference<GithubUser>() {});
        return page(githubId, "https://api.github.com/users/" + name + "/repos?per_page=" + PAGE_SIZE
                + "&page=" + checkedPage(page) + "&sort=updated&type=owner", page);
    }

    public GithubRepoDTO exact(String githubId, String owner, String repo) {
        String path = checkedUser(owner) + "/" + checkedRepo(repo);
        GithubRepoDTO result = request(githubId, "https://api.github.com/repos/" + path,
                new ParameterizedTypeReference<GithubRepoDTO>() {}).getBody();
        if (result == null || result.getId() == null || result.getOwner() == null) {
            throw new GithubDiscoveryException(HttpStatus.BAD_GATEWAY, "GitHub repository details are unavailable.");
        }
        return result;
    }

    public GithubProject store(String githubId, String owner, String repo) {
        // Use canonical GitHub metadata, never the browser's repository ID or owner assertion.
        return githubService.saveSelectedRepository(exact(githubId, owner, repo), githubId);
    }

    private RepositoryPageDTO page(String githubId, String url, int page) {
        ResponseEntity<List<GithubRepoDTO>> response = request(githubId, url,
                new ParameterizedTypeReference<List<GithubRepoDTO>>() {});
        List<GithubRepoDTO> repos = response.getBody() == null ? List.of() : response.getBody();
        Set<Long> stored = projects.findByUserGithubIdOrderByIdAsc(githubId).stream()
                .map(GithubProject::getGithubRepoId).collect(Collectors.toSet());
        String link = response.getHeaders().getFirst("Link");
        return new RepositoryPageDTO(repos, stored, page, link != null && link.contains("rel=\"next\""));
    }

    private <T> ResponseEntity<T> request(String githubId, String url, ParameterizedTypeReference<T> type) {
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient("github", githubId);
        if (client == null || client.getAccessToken() == null) {
            throw new GithubDiscoveryException(HttpStatus.UNAUTHORIZED,
                    "Your GitHub session needs to be refreshed. Sign in with GitHub again.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(client.getAccessToken().getTokenValue());
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        try {
            return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), type);
        } catch (HttpClientErrorException ex) {
            int status = ex.getStatusCode().value();
            if (status == 429 || (status == 403 && (ex.getResponseBodyAsString().toLowerCase().contains("rate limit")
                    || "0".equals(ex.getResponseHeaders() == null ? null
                    : ex.getResponseHeaders().getFirst("X-RateLimit-Remaining"))
                    || ex.getResponseHeaders() != null && ex.getResponseHeaders().containsKey("Retry-After")))) {
                throw new GithubDiscoveryException(HttpStatus.TOO_MANY_REQUESTS,
                        "GitHub request limit reached. Please wait a moment and try again.");
            }
            if (status == 401) throw new GithubDiscoveryException(HttpStatus.UNAUTHORIZED,
                    "Your GitHub session needs to be refreshed. Sign in with GitHub again.");
            if (status == 404) throw new GithubDiscoveryException(HttpStatus.NOT_FOUND,
                    "GitHub user or repository not found or inaccessible.");
            if (status == 403) throw new GithubDiscoveryException(HttpStatus.FORBIDDEN,
                    "GitHub did not allow access to these repositories.");
            throw new GithubDiscoveryException(HttpStatus.BAD_GATEWAY, "GitHub repository lookup failed.");
        } catch (RestClientException ex) {
            throw new GithubDiscoveryException(HttpStatus.BAD_GATEWAY, "GitHub is temporarily unavailable.");
        }
    }

    private int checkedPage(int page) {
        if (page < 1 || page > 100) throw new GithubDiscoveryException(HttpStatus.BAD_REQUEST, "Invalid repository page.");
        return page;
    }

    private String checkedUser(String name) {
        if (name == null || !name.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new GithubDiscoveryException(HttpStatus.BAD_REQUEST, "Invalid GitHub username.");
        }
        return name;
    }

    private String checkedRepo(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,100}")) {
            throw new GithubDiscoveryException(HttpStatus.BAD_REQUEST, "Invalid GitHub repository name.");
        }
        return name;
    }

    private record GithubUser(String login) {}
}
