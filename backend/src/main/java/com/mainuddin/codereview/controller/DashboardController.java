package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.dto.DashboardResponseDTO;
import com.mainuddin.codereview.security.JwtUtils;
import com.mainuddin.codereview.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JwtUtils jwtUtils;

    @GetMapping("/summary")
    public ResponseEntity<DashboardResponseDTO> getDashboardSummary(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (jwtUtils.validateJwtToken(token)) {
            String githubId = jwtUtils.getGithubIdFromJwtToken(token);
            DashboardResponseDTO dashboardData = dashboardService.getDashboardData(githubId);
            return ResponseEntity.ok(dashboardData);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
