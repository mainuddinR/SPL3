package com.mainuddin.codereview.security;

import com.mainuddin.codereview.entity.User;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.UserRepository;
import com.mainuddin.codereview.service.GithubPullRequestClient;
import com.mainuddin.codereview.service.UiStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubAuthorizedClientLoginTest {
    private static final String TEST_TOKEN = "unit-test-oauth-token";

    @Test
    void successfulLoginMakesClientAvailableByImmutableGithubIdToStatusAndMonitor() throws Exception {
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.test/authorize")
                .tokenUri("https://example.test/token")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("login")
                .clientName("GitHub").build();
        var store = new InMemoryOAuth2AuthorizedClientService(
                new InMemoryClientRegistrationRepository(registration));
        var resolver = new GithubAuthorizedClientService(store);
        var principal = new DefaultOAuth2User(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", 123L, "login", "renamed-user"), "login");
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github");
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                TEST_TOKEN, Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        // OAuth2LoginAuthenticationFilter saves this before invoking the success handler.
        store.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, authentication.getName(), token), authentication);
        assertNull(store.loadAuthorizedClient("github", "123"));

        UserRepository users = mock(UserRepository.class);
        when(users.findByGithubId("123")).thenReturn(Optional.of(User.builder()
                .githubId("123").username("renamed-user").role("ROLE_USER").build()));
        JwtUtils jwt = mock(JwtUtils.class);
        when(jwt.generateJwtToken("123", "renamed-user")).thenReturn("unit-test-jwt");
        var handler = new CustomOAuth2SuccessHandler();
        ReflectionTestUtils.setField(handler, "userRepository", users);
        ReflectionTestUtils.setField(handler, "jwtUtils", jwt);
        ReflectionTestUtils.setField(handler, "authorizedClients", resolver);
        HttpServletResponse response = mock(HttpServletResponse.class);
        handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response, authentication);

        assertNotNull(store.loadAuthorizedClient("github", "123"));
        assertNull(store.loadAuthorizedClient("github", "renamed-user-duplicate"));
        assertTrue(resolver.hasValidClient("123"));
        var status = new UiStatusService(users, mock(GithubProjectRepository.class), resolver,
                mock(DataSource.class), "http://localhost:8001", false, "", "test", true);
        assertTrue(status.githubMonitorStatus("123").githubSessionConnected());
        var monitorClient = new GithubPullRequestClient(mock(org.springframework.web.client.RestTemplate.class), resolver);
        assertTrue(monitorClient.hasValidSession("123"));
        verify(response).sendRedirect("http://localhost:4200/login/success?token=unit-test-jwt");
        assertFalse(status.githubMonitorStatus("123").toString().contains(TEST_TOKEN));
    }

    @Test
    void absentClientRequiresReconnectWhileStoredTokenRemainsUsableUntilProviderRejectsIt() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.test/authorize")
                .tokenUri("https://example.test/token")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("id").clientName("GitHub").build();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var store = new InMemoryOAuth2AuthorizedClientService(registrations);
        var resolver = new GithubAuthorizedClientService(store);
        assertFalse(resolver.hasValidClient("123"));
        var principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("123", null);
        var tokenWithPastExpiryMetadata = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                TEST_TOKEN, Instant.now().minusSeconds(100), Instant.now().minusSeconds(1));
        store.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, "123", tokenWithPastExpiryMetadata), principal);

        // GitHub is the source of truth for token validity. A stored nonblank token remains usable
        // until a real provider request returns 401 and triggers the reconnect-required flow.
        assertTrue(resolver.hasValidClient("123"));
        assertNotNull(resolver.loadValidForGithubId("123"));

        // The authorized-client store is intentionally in-memory; after a backend restart/recreated
        // store, the user must sign in with GitHub again.
        assertFalse(new GithubAuthorizedClientService(new InMemoryOAuth2AuthorizedClientService(registrations))
                .hasValidClient("123"));
    }
}
