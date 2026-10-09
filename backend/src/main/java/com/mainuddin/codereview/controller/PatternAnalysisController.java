package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.design.PatternConformanceDTO;
import com.mainuddin.codereview.design.PatternConformanceService;
import com.mainuddin.codereview.design.PatternProfileDTO;
import com.mainuddin.codereview.design.RepositoryPatternProfileService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PatternAnalysisController {
    private final RepositoryPatternProfileService profiles;
    private final PatternConformanceService checks;
    private final com.mainuddin.codereview.design.DesignAdviceService advice;

    public PatternAnalysisController(RepositoryPatternProfileService profiles, PatternConformanceService checks,
            com.mainuddin.codereview.design.DesignAdviceService advice) {
        this.profiles = profiles; this.checks = checks; this.advice = advice;
    }

    @GetMapping("/projects/{projectId}/design-profile")
    public PatternProfileDTO profile(@PathVariable Long projectId) {
        return profiles.latest(projectId, githubId());
    }

    @PostMapping("/projects/{projectId}/design-profile")
    public PatternProfileDTO refresh(@PathVariable Long projectId) {
        return profiles.start(projectId, githubId());
    }

    @GetMapping("/prs/{prId}/design-conformance")
    public PatternConformanceDTO conformance(@PathVariable Long prId) {
        return checks.latest(prId, githubId());
    }

    @GetMapping("/prs/{prId}/design-profile")
    public PatternProfileDTO profileForPullRequest(@PathVariable Long prId) {
        String owner = githubId();
        Long projectId = checks.repositoryId(prId, owner);
        PatternProfileDTO profile = profiles.latest(projectId, owner);
        return profile != null ? profile : new PatternProfileDTO(null, projectId, "NOT_ANALYZED",
                "Not analyzed", null, null, null, null, null, null, null, java.util.List.of());
    }

    @PostMapping("/prs/{prId}/design-conformance")
    public PatternConformanceDTO check(@PathVariable Long prId) {
        return checks.start(prId, githubId());
    }

    @PostMapping("/prs/{prId}/design-conformance/{checkId}/findings/{findingIndex}/suggestion")
    public com.mainuddin.codereview.design.DesignAdviceService.Response suggestion(@PathVariable Long prId,
            @PathVariable Long checkId, @PathVariable int findingIndex) {
        return advice.generate(prId, checkId, findingIndex, githubId());
    }

    private String githubId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
