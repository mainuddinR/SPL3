package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.dto.GithubBranchDTO;
import com.mainuddin.codereview.dto.GithubRepoDTO;
import com.mainuddin.codereview.dto.GithubSearchResponseDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.service.GithubService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/github")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GithubController {

    private final GithubService githubService;

    public GithubController(GithubService githubService) {
        this.githubService = githubService;
    }

    @GetMapping("/search")
    public ResponseEntity<GithubSearchResponseDTO> searchRepositories(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(githubService.searchRepositories(q, page));
    }

    @GetMapping("/repos/{owner}/{repo}")
    public ResponseEntity<GithubRepoDTO> getRepositoryDetails(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(githubService.getRepositoryDetails(owner, repo));
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GithubBranchDTO>> getBranches(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(githubService.getBranches(owner, repo));
    }

    @PostMapping("/repos/select")
    public ResponseEntity<GithubProject> selectRepository(@RequestBody GithubRepoDTO repoDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String githubId = authentication.getName(); // the principal is now githubId
        
        return ResponseEntity.ok(githubService.saveSelectedRepository(repoDTO, githubId));
    }
}
