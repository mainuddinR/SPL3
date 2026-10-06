package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.dto.DashboardResponseDTO;
import com.mainuddin.codereview.security.JwtUtils;
import com.mainuddin.codereview.service.DashboardService;
import com.mainuddin.codereview.service.DashboardDrilldownService;
import com.mainuddin.codereview.service.DashboardFindingDetailService;
import com.mainuddin.codereview.dto.*;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private DashboardDrilldownService drilldowns;

    @Autowired
    private DashboardFindingDetailService findingDetails;

    @GetMapping("/findings/{findingId}")
    public DashboardFindingDetailDTO findingDetail(@org.springframework.web.bind.annotation.PathVariable Long findingId) {
        return findingDetails.get(findingId);
    }

    @GetMapping("/repositories")
    public DashboardPageDTO<DashboardRepositoryRowDTO> repositories(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return drilldowns.repositories(owner(), page, size);
    }

    @GetMapping("/pull-requests")
    public DashboardPageDTO<DashboardPullRequestRowDTO> pullRequests(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long repositoryId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String state,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean analyzed,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q) {
        return drilldowns.pullRequests(owner(), page, size, repositoryId, state, analyzed, q);
    }

    @GetMapping("/analyzed-prs")
    public DashboardPageDTO<DashboardAnalyzedRowDTO> analyzedPullRequests(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long repositoryId) {
        return drilldowns.analyzed(owner(), page, size, repositoryId);
    }

    @GetMapping("/findings")
    public DashboardPageDTO<DashboardFindingRowDTO> currentFindings(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long repositoryId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String category,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String severity,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String aiSuggestion,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q) {
        return drilldowns.findings(owner(), page, size, repositoryId, category, severity, aiSuggestion, q);
    }

    private String owner() { return SecurityContextHolder.getContext().getAuthentication().getName(); }

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
