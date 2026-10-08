package com.mainuddin.codereview.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Resolves the server-side GitHub client by the immutable ID stored with users and projects. */
@Service
public class GithubAuthorizedClientService {
    private static final Logger log = LoggerFactory.getLogger(GithubAuthorizedClientService.class);
    private static final String REGISTRATION_ID = "github";
    private final OAuth2AuthorizedClientService clients;

    public GithubAuthorizedClientService(OAuth2AuthorizedClientService clients) {
        this.clients = clients;
    }

    /** OAuth login stores under Authentication#getName; make the same client available by GitHub ID. */
    public void linkLogin(Authentication authentication, String githubId) {
        String principalName = authentication.getName();
        OAuth2AuthorizedClient client = clients.loadAuthorizedClient(REGISTRATION_ID, principalName);
        Instant expiresAt = client == null || client.getAccessToken() == null ? null : client.getAccessToken().getExpiresAt();
        boolean valid = isValid(client);
        log.info("GitHub OAuth client registration={} principalName={} githubId={} exists={} tokenPresent={} expiresAt={} valid={}",
                REGISTRATION_ID, principalName, githubId, client != null,
                client != null && client.getAccessToken() != null, expiresAt, valid);
        if (client == null || client.getAccessToken() == null) {
            return;
        }
        if (!githubId.equals(principalName)) {
            Authentication alias = new UsernamePasswordAuthenticationToken(
                    githubId, null, authentication.getAuthorities());
            clients.saveAuthorizedClient(client, alias);
        }
    }

    public OAuth2AuthorizedClient loadForGithubId(String githubId) {
        return clients.loadAuthorizedClient(REGISTRATION_ID, githubId);
    }

    public boolean hasValidClient(String githubId) {
        OAuth2AuthorizedClient client = loadForGithubId(githubId);
        boolean valid = isValid(client);
        if (!valid) {
            Instant expiresAt = client == null || client.getAccessToken() == null ? null : client.getAccessToken().getExpiresAt();
            log.info("GitHub OAuth lookup githubId={} exists={} tokenPresent={} expiresAt={} valid=false",
                    githubId, client != null, client != null && client.getAccessToken() != null, expiresAt);
        }
        return valid;
    }

    public OAuth2AuthorizedClient loadValidForGithubId(String githubId) {
        OAuth2AuthorizedClient client = loadForGithubId(githubId);
        return isValid(client) ? client : null;
    }

    private boolean isValid(OAuth2AuthorizedClient client) {
        /*
         * Treat a stored GitHub authorized client with an access token as usable.
         * GitHub itself remains the source of truth: downstream clients already
         * translate a real GitHub HTTP 401 into the reconnect-required flow.
         * This avoids falsely rejecting a freshly issued token based only on
         * provider expiry metadata.
         */
        return client != null
                && client.getAccessToken() != null
                && client.getAccessToken().getTokenValue() != null
                && !client.getAccessToken().getTokenValue().isBlank();
    }
}
