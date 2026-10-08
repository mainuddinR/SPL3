package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.GithubRepoDTO;
import com.mainuddin.codereview.dto.RepositoryPageDTO;
import com.mainuddin.codereview.dto.GithubUserSearchDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.security.GithubAuthorizedClientService;
import com.mainuddin.codereview.design.RepositoryPatternProfileService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GithubDiscoveryService {
    private static final int PAGE_SIZE = 50;
    private final RestTemplate http;
    private final GithubAuthorizedClientService authorizedClients;
    private final GithubProjectRepository projects;
    private final GithubService githubService;
    private final RepositoryPatternProfileService patternProfiles;

    public GithubDiscoveryService(RestTemplate http, GithubAuthorizedClientService authorizedClients,
                                  GithubProjectRepository projects, GithubService githubService,
                                  RepositoryPatternProfileService patternProfiles) {
        this.http = http; this.authorizedClients = authorizedClients;
        this.projects = projects; this.githubService = githubService; this.patternProfiles = patternProfiles;
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

    public GithubUserSearchDTO searchUsers(String githubId, String query, int page) {
        String q = checkedQuery(query);
        int checked = checkedPage(page);
        ResponseEntity<SearchEnvelope<GithubUserSearchDTO.UserResult>> response = request(githubId,
                "https://api.github.com/search/users?q=" + encoded(q) + "&per_page=" + PAGE_SIZE + "&page=" + checked,
                new ParameterizedTypeReference<SearchEnvelope<GithubUserSearchDTO.UserResult>>() {});
        var body = response.getBody();
        return new GithubUserSearchDTO(body == null || body.items() == null ? List.of() : body.items(),
                checked, hasNext(response));
    }

    public RepositoryPageDTO searchRepositories(String githubId, String query, int page) {
        String q = checkedQuery(query);
        int checked = checkedPage(page);
        if (q.matches("[A-Za-z0-9-]{1,39}/[A-Za-z0-9_.-]{1,100}")) {
            if (checked > 1) return new RepositoryPageDTO(List.of(), Set.of(), checked, false);
            String[] parts = q.split("/", 2);
            GithubRepoDTO repo = exact(githubId, parts[0], parts[1]);
            return new RepositoryPageDTO(List.of(repo), storedIds(githubId), 1, false);
        }
        ResponseEntity<SearchEnvelope<GithubRepoDTO>> response = request(githubId,
                "https://api.github.com/search/repositories?q=" + encoded(q) + "&per_page=" + PAGE_SIZE + "&page=" + checked,
                new ParameterizedTypeReference<SearchEnvelope<GithubRepoDTO>>() {});
        var body = response.getBody();
        return new RepositoryPageDTO(body == null || body.items() == null ? List.of() : body.items(),
                storedIds(githubId), checked, hasNext(response));
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
        GithubProject saved = githubService.saveSelectedRepository(exact(githubId, owner, repo), githubId);
        try { patternProfiles.ensureInitialProfile(saved.getId(), githubId); }
        catch (RuntimeException ignored) { /* Repository selection remains usable if optional design analysis cannot start. */ }
        return saved;
    }

    private RepositoryPageDTO page(String githubId, String url, int page) {
        ResponseEntity<List<GithubRepoDTO>> response = request(githubId, url,
                new ParameterizedTypeReference<List<GithubRepoDTO>>() {});
        List<GithubRepoDTO> repos = response.getBody() == null ? List.of() : response.getBody();
        return new RepositoryPageDTO(repos, storedIds(githubId), page, hasNext(response));
    }

    private Set<Long> storedIds(String githubId) {
        return projects.findByUserGithubIdOrderByIdAsc(githubId).stream()
                .map(GithubProject::getGithubRepoId).collect(Collectors.toSet());
    }

    private boolean hasNext(ResponseEntity<?> response) {
        String link = response.getHeaders().getFirst("Link");
        return link != null && link.contains("rel=\"next\"");
    }

    private String checkedQuery(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 3 || trimmed.length() > 100 || trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new GithubDiscoveryException(HttpStatus.BAD_REQUEST, "Enter at least 3 search characters.");
        }
        return trimmed;
    }

    private String encoded(String query) { return URLEncoder.encode(query, StandardCharsets.UTF_8); }

    private record SearchEnvelope<T>(List<T> items) {}

    private <T> ResponseEntity<T> request(String githubId, String url, ParameterizedTypeReference<T> type) {
        OAuth2AuthorizedClient client = authorizedClients.loadValidForGithubId(githubId);
        if (client == null) {
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
