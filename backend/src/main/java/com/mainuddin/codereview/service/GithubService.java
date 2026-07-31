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
import jakarta.transaction.Transactional;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.entity.PullRequestComment;
import com.mainuddin.codereview.dto.GithubPullRequestDTO;
import com.mainuddin.codereview.dto.GithubFileDTO;
import com.mainuddin.codereview.dto.GithubCommentDTO;

@Service
public class GithubService {

    private final RestTemplate restTemplate;
    private final GithubProjectRepository githubProjectRepository;
    private final UserRepository userRepository;
    private final com.mainuddin.codereview.repository.PullRequestRepository pullRequestRepository;
    private final com.mainuddin.codereview.repository.PullRequestFileRepository pullRequestFileRepository;
    private final com.mainuddin.codereview.repository.PullRequestCommentRepository pullRequestCommentRepository;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String githubClientId;

    public GithubService(RestTemplate restTemplate, GithubProjectRepository githubProjectRepository, UserRepository userRepository,
                         com.mainuddin.codereview.repository.PullRequestRepository pullRequestRepository,
                         com.mainuddin.codereview.repository.PullRequestFileRepository pullRequestFileRepository,
                         com.mainuddin.codereview.repository.PullRequestCommentRepository pullRequestCommentRepository) {
        this.restTemplate = restTemplate;
        this.githubProjectRepository = githubProjectRepository;
        this.userRepository = userRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestFileRepository = pullRequestFileRepository;
        this.pullRequestCommentRepository = pullRequestCommentRepository;
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
                pr.setGithubProject(project);
                
                pullRequestRepository.save(pr);
            }
        }
    }

    @Transactional
    public void fetchAndStorePullRequestDetails(Long prId) {
        PullRequest pr = pullRequestRepository.findById(prId)
                .orElseThrow(() -> new RuntimeException("PR not found"));
        GithubProject project = pr.getGithubProject();

        // 1. Fetch Files
        String filesUrl = String.format("https://api.github.com/repos/%s/%s/pulls/%d/files", 
                project.getOwnerLogin(), project.getName(), pr.getNumber());
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        
        try {
            ResponseEntity<List<GithubFileDTO>> filesResponse = restTemplate.exchange(
                    filesUrl, HttpMethod.GET, entity, new ParameterizedTypeReference<List<GithubFileDTO>>() {});
                    
            if (filesResponse.getBody() != null) {
                pullRequestFileRepository.deleteByPullRequestId(pr.getId()); // clean old files
                for (GithubFileDTO fileDto : filesResponse.getBody()) {
                    PullRequestFile file = PullRequestFile.builder()
                            .sha(fileDto.getSha())
                            .filename(fileDto.getFilename())
                            .status(fileDto.getStatus())
                            .additions(fileDto.getAdditions())
                            .deletions(fileDto.getDeletions())
                            .changes(fileDto.getChanges())
                            .patch(fileDto.getPatch())
                            .pullRequest(pr)
                            .build();
                    pullRequestFileRepository.save(file);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch files for PR " + pr.getNumber());
        }

        // 2. Fetch Comments
        String commentsUrl = String.format("https://api.github.com/repos/%s/%s/pulls/%d/comments", 
                project.getOwnerLogin(), project.getName(), pr.getNumber());
        try {
            ResponseEntity<List<GithubCommentDTO>> commentsResponse = restTemplate.exchange(
                    commentsUrl, HttpMethod.GET, entity, new ParameterizedTypeReference<List<GithubCommentDTO>>() {});
                    
            if (commentsResponse.getBody() != null) {
                pullRequestCommentRepository.deleteByPullRequestId(pr.getId()); // clean old comments
                for (GithubCommentDTO commentDto : commentsResponse.getBody()) {
                    PullRequestComment comment = PullRequestComment.builder()
                            .githubCommentId(commentDto.getId())
                            .path(commentDto.getPath())
                            .position(commentDto.getPosition())
                            .body(commentDto.getBody())
                            .userLogin(commentDto.getUser() != null ? commentDto.getUser().getLogin() : null)
                            .createdAt(commentDto.getCreatedAt())
                            .pullRequest(pr)
                            .build();
                    pullRequestCommentRepository.save(comment);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch comments for PR " + pr.getNumber());
        }
    }
}
