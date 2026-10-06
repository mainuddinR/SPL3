package com.mainuddin.codereview.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class GitHubReviewClient {
    private final RestTemplate http;
    private final OAuth2AuthorizedClientService authorizedClients;

    public GitHubReviewClient(RestTemplate http, OAuth2AuthorizedClientService authorizedClients) {
        this.http = http;
        this.authorizedClients = authorizedClients;
    }

    public String currentHead(String githubId, String owner, String repo, int number) {
        PullHead response = exchange(githubId, url(owner, repo, number), HttpMethod.GET, null, PullHead.class);
        if (response == null || response.head() == null || response.head().sha() == null) {
            throw new ReviewPublishException(HttpStatus.BAD_GATEWAY, "GitHub did not return a PR revision.");
        }
        return response.head().sha();
    }

    public PublishedReview publish(String githubId, String owner, String repo, int number,
                                   String sha, String summary, List<ReviewComment> comments) {
        PublishedReview response = exchange(githubId, url(owner, repo, number) + "/reviews", HttpMethod.POST,
                new ReviewRequest(sha, summary, "COMMENT", comments), PublishedReview.class);
        if (response == null || response.id() == null) {
            throw new ReviewPublishException(HttpStatus.BAD_GATEWAY, "GitHub review response was incomplete. Check GitHub before retrying.");
        }
        return response;
    }

    private <T> T exchange(String githubId, String url, HttpMethod method, Object body, Class<T> type) {
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient("github", githubId);
        if (client == null || client.getAccessToken() == null) {
            throw new ReviewPublishException(HttpStatus.UNAUTHORIZED, "GitHub authorization is unavailable. Sign in with GitHub again.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(client.getAccessToken().getTokenValue());
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        try {
            return http.exchange(url, method, new HttpEntity<>(body, headers), type).getBody();
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 401 || code == 403) throw new ReviewPublishException(HttpStatus.FORBIDDEN,
                    "GitHub authorization does not permit this review. Sign in again and check repository access.");
            if (code == 404) throw new ReviewPublishException(HttpStatus.NOT_FOUND, "GitHub pull request not found or inaccessible.");
            if (code == 422) throw new ReviewPublishException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "GitHub rejected one or more review locations. Check the PR diff and reanalyze.");
            throw new ReviewPublishException(HttpStatus.BAD_GATEWAY, "GitHub could not publish the review. Check GitHub before retrying.");
        } catch (RestClientException ex) {
            throw new ReviewPublishException(HttpStatus.BAD_GATEWAY,
                    "GitHub review request failed. Check GitHub before retrying.");
        }
    }

    private String url(String owner, String repo, int number) {
        if (!owner.matches("[A-Za-z0-9-]+") || !repo.matches("[A-Za-z0-9_.-]+") || number < 1) {
            throw new ReviewPublishException(HttpStatus.BAD_REQUEST, "Invalid saved GitHub repository or PR number.");
        }
        return "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + number;
    }

    private record PullHead(Head head) {}
    private record Head(String sha) {}
    private record ReviewRequest(@JsonProperty("commit_id") String commitId, String body,
                                 String event, List<ReviewComment> comments) {}
    public record ReviewComment(String path, int line, String side, String body) {}
    public record PublishedReview(Long id, @JsonProperty("html_url") String htmlUrl) {}
}
