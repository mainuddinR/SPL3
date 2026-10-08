package com.mainuddin.codereview.security;

import com.mainuddin.codereview.entity.User;
import com.mainuddin.codereview.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private GithubAuthorizedClientService authorizedClients;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String githubId = oAuth2User.getAttribute("id").toString();
        String username = oAuth2User.getAttribute("login");
        String email = oAuth2User.getAttribute("email");
        String avatarUrl = oAuth2User.getAttribute("avatar_url");

        User user = userRepository.findByGithubId(githubId).orElseGet(() -> {
            User newUser = User.builder()
                    .githubId(githubId)
                    .username(username)
                    .email(email)
                    .avatarUrl(avatarUrl)
                    .role("ROLE_USER")
                    .build();
            return userRepository.save(newUser);
        });

        // Keep the stored profile current when GitHub changes username/avatar/email.
        boolean profileChanged = !java.util.Objects.equals(user.getUsername(), username)
                || !java.util.Objects.equals(user.getEmail(), email)
                || !java.util.Objects.equals(user.getAvatarUrl(), avatarUrl);
        if (profileChanged) {
            user.setUsername(username);
            user.setEmail(email);
            user.setAvatarUrl(avatarUrl);
            user = userRepository.save(user);
        }

        authorizedClients.linkLogin(authentication, githubId);

        String token = jwtUtils.generateJwtToken(user.getGithubId(), user.getUsername());
        response.sendRedirect("http://localhost:4200/login/success?token=" + token);
    }

}
