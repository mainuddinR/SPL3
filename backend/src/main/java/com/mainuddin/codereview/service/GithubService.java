package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.GithubBranchDTO;
import com.mainuddin.codereview.dto.GithubRepoDTO;
import com.mainuddin.codereview.dto.GithubSearchResponseDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.User;
import com.mainuddin.codereview.repository.GithubProjectRepository;
import com.mainuddin.codereview.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Objects;
import jakarta.transaction.Transactional;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.dto.GithubPullRequestDTO;
import com.mainuddin.codereview.dto.GithubFileDTO;
import com.mainuddin.codereview.dto.GithubCommentDTO;

@Service
public class GithubService {

    private final RestTemplate restTemplate;
    private final GithubProjectRepository githubProjectRepository;
    private final UserRepository userRepository;
    private final com.mainuddin.codereview.repository.PullRequestRepository pullRequestRepository;
    private final PullRequestFileSnapshotService fileSnapshotService;
    private final PullRequestCommentSnapshotService commentSnapshotService;

    private static final int FILES_PER_PAGE = 100;
    private static final int MAX_FILE_PAGES = 30; // GitHub exposes at most 3,000 PR files.

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String githubClientId;

    public GithubService(RestTemplate restTemplate, GithubProjectRepository githubProjectRepository, UserRepository userRepository,
                         com.mainuddin.codereview.repository.PullRequestRepository pullRequestRepository,
                         PullRequestFileSnapshotService fileSnapshotService,
                         PullRequestCommentSnapshotService commentSnapshotService) {
        this.restTemplate = restTemplate;
        this.githubProjectRepository = githubProjectRepository;
        this.userRepository = userRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.fileSnapshotService = fileSnapshotService;
        this.commentSnapshotService = commentSnapshotService;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        // For production, we would inject the user's OAuth2 access token here.
        // headers.setBearerAuth(userAccessToken);
        return headers;
    }

    public GithubSearchResponseDTO searchRepositories(String query, int page) {
        String url = String.format("https://api.github.com/search/repositories?q=%s&page=%d&per_page=10", query, page);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        
        ResponseEntity<GithubSearchResponseDTO> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, GithubSearchResponseDTO.class);
                
        return response.getBody();
    }

    public GithubRepoDTO getRepositoryDetails(String owner, String repo) {
        String url = String.format("https://api.github.com/repos/%s/%s", owner, repo);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        
        ResponseEntity<GithubRepoDTO> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, GithubRepoDTO.class);
                
        return response.getBody();
    }

    public List<GithubBranchDTO> getBranches(String owner, String repo) {
        String url = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        
        ResponseEntity<List<GithubBranchDTO>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<GithubBranchDTO>>() {});
                
        return response.getBody();
    }

    public GithubProject saveSelectedRepository(GithubRepoDTO repoDTO, String githubId) {
        User user = userRepository.findByGithubId(githubId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (githubProjectRepository.existsByGithubRepoIdAndUserId(repoDTO.getId(), user.getId())) {
            throw new RuntimeException("Repository already saved for this user");
        }

        GithubProject project = GithubProject.builder()
                .githubRepoId(repoDTO.getId())
                .name(repoDTO.getName())
                .fullName(repoDTO.getFullName())
                .htmlUrl(repoDTO.getHtmlUrl())
                .description(repoDTO.getDescription())
                .language(repoDTO.getLanguage())
                .ownerLogin(repoDTO.getOwner().getLogin())
                .ownerAvatarUrl(repoDTO.getOwner().getAvatarUrl())
                .user(user)
                .build();

        return githubProjectRepository.save(project);
    }

    @Transactional
    public void fetchAndStorePullRequests(Long projectId) {
        GithubProject project = githubProjectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        String url = String.format("https://api.github.com/repos/%s/%s/pulls?state=all&per_page=100", 
                project.getOwnerLogin(), project.getName());
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        
        ResponseEntity<List<GithubPullRequestDTO>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<GithubPullRequestDTO>>() {});
                
        List<GithubPullRequestDTO> prs = response.getBody();
        if (prs != null) {
            for (GithubPullRequestDTO prDto : prs) {
                PullRequest pr = pullRequestRepository.findByGithubPrId(prDto.getId())
                        .orElse(new PullRequest());
                
                pr.setGithubPrId(prDto.getId());
                pr.setNumber(prDto.getNumber());
                pr.setTitle(prDto.getTitle());
                pr.setState(prDto.getState());
                pr.setHtmlUrl(prDto.getHtmlUrl());
                pr.setBody(prDto.getBody());
                pr.setCreatedAt(prDto.getCreatedAt());
                pr.setUpdatedAt(prDto.getUpdatedAt());
                pr.setHeadSha(sha(prDto.getHead()));
                pr.setBaseSha(sha(prDto.getBase()));
                pr.setGithubProject(project);
                
                pullRequestRepository.save(pr);
            }
        }
    }

    public void fetchAndStorePullRequestDetails(Long prId) {
        PullRequest pr = pullRequestRepository.findWithProjectById(prId)
                .orElseThrow(() -> new RuntimeException("PR not found"));
        GithubProject project = pr.getGithubProject();
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        String pullUrl = String.format("https://api.github.com/repos/%s/%s/pulls/%d",
                project.getOwnerLogin(), project.getName(), pr.getNumber());
        GithubPullRequestDTO before = requireRevision(restTemplate.exchange(
                pullUrl, HttpMethod.GET, entity, GithubPullRequestDTO.class).getBody());
        List<GithubFileDTO> files = new ArrayList<>();
        boolean complete = false;
        for (int page = 1; page <= MAX_FILE_PAGES; page++) {
            String filesUrl = pullUrl + "/files?per_page=" + FILES_PER_PAGE + "&page=" + page;
            List<GithubFileDTO> batch = restTemplate.exchange(filesUrl, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<GithubFileDTO>>() {}).getBody();
            if (batch == null || batch.size() > FILES_PER_PAGE) {
                throw new IllegalStateException("Incomplete PR file response");
            }
            files.addAll(batch);
            if (batch.size() < FILES_PER_PAGE) {
                complete = true;
                break;
            }
        }
        if (!complete) {
            throw new IllegalStateException("GitHub PR file ceiling reached; completeness cannot be established");
        }
        GithubPullRequestDTO after = requireRevision(restTemplate.exchange(
                pullUrl, HttpMethod.GET, entity, GithubPullRequestDTO.class).getBody());
        if (!Objects.equals(sha(before.getHead()), sha(after.getHead())) ||
                !Objects.equals(sha(before.getBase()), sha(after.getBase()))) {
            throw new IllegalStateException("PR revision changed during file synchronization");
        }
        fileSnapshotService.replace(prId, sha(after.getHead()), sha(after.getBase()), files);

        // 2. Fetch Comments
        String commentsUrl = String.format("https://api.github.com/repos/%s/%s/pulls/%d/comments", 
                project.getOwnerLogin(), project.getName(), pr.getNumber());
        try {
            ResponseEntity<List<GithubCommentDTO>> commentsResponse = restTemplate.exchange(
                    commentsUrl, HttpMethod.GET, entity, new ParameterizedTypeReference<List<GithubCommentDTO>>() {});
                    
            if (commentsResponse.getBody() != null) {
                commentSnapshotService.replace(prId, commentsResponse.getBody());
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch comments for PR " + pr.getNumber());
        }
    }

    private static String sha(GithubPullRequestDTO.GitRef ref) {
        return ref == null ? null : ref.getSha();
    }

    private static GithubPullRequestDTO requireRevision(GithubPullRequestDTO dto) {
        if (dto == null || sha(dto.getHead()) == null || sha(dto.getHead()).isBlank() ||
                sha(dto.getBase()) == null || sha(dto.getBase()).isBlank()) {
            throw new IllegalStateException("GitHub PR revision unavailable");
        }
        return dto;
    }
}
