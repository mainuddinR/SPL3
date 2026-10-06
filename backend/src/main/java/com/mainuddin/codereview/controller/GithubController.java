package com.mainuddin.codereview.controller;

import com.mainuddin.codereview.dto.GithubBranchDTO;
import com.mainuddin.codereview.dto.GithubRepoDTO;
import com.mainuddin.codereview.dto.RepositoryPageDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.service.GithubService;
import com.mainuddin.codereview.service.GithubDiscoveryService;
import com.mainuddin.codereview.service.GithubDiscoveryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/github")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GithubController {

    private final GithubService githubService;
    private final GithubDiscoveryService discovery;

    public GithubController(GithubService githubService, GithubDiscoveryService discovery) {
        this.githubService = githubService;
        this.discovery = discovery;
    }

    @GetMapping("/repositories/me")
    public ResponseEntity<RepositoryPageDTO> myRepositories(@RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(discovery.mine(githubId(), page));
    }

    @GetMapping("/users/{username}/repositories")
    public ResponseEntity<RepositoryPageDTO> userRepositories(@PathVariable String username,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(discovery.byUser(githubId(), username, page));
    }

    @GetMapping("/repositories/{owner}/{repo}")
    public ResponseEntity<GithubRepoDTO> exactRepository(@PathVariable String owner, @PathVariable String repo) {
        return ResponseEntity.ok(discovery.exact(githubId(), owner, repo));
    }

    @GetMapping("/repos/{owner}/{repo}")
    public ResponseEntity<GithubRepoDTO> getRepositoryDetails(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(discovery.exact(githubId(), owner, repo));
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GithubBranchDTO>> getBranches(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(githubService.getBranches(owner, repo));
    }

    @PostMapping("/repos/select")
    public ResponseEntity<GithubProject> selectRepository(@RequestBody GithubRepoDTO repoDTO) {
        if (repoDTO == null || repoDTO.getOwner() == null) {
            throw new GithubDiscoveryException(HttpStatus.BAD_REQUEST, "Select a valid GitHub repository.");
        }
        return ResponseEntity.ok(discovery.store(githubId(), repoDTO.getOwner().getLogin(), repoDTO.getName()));
    }

    private String githubId() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}
