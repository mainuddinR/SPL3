package com.mainuddin.codereview.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;

@Component
public class CustomOAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        // Redirect to the frontend login page with an error parameter
        response.sendRedirect(frontendUrl + "/login?error=github-login-failed");
    }
}
