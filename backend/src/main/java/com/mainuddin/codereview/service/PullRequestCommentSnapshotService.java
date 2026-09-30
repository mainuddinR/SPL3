package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.GithubCommentDTO;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestComment;
import com.mainuddin.codereview.repository.PullRequestCommentRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PullRequestCommentSnapshotService {
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestCommentRepository commentRepository;

    public PullRequestCommentSnapshotService(PullRequestRepository pullRequestRepository,
                                             PullRequestCommentRepository commentRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public void replace(Long prId, List<GithubCommentDTO> comments) {
        PullRequest pr = pullRequestRepository.findById(prId).orElseThrow();
        commentRepository.deleteByPullRequestId(prId);
        for (GithubCommentDTO dto : comments) {
            commentRepository.save(PullRequestComment.builder()
                    .githubCommentId(dto.getId())
                    .path(dto.getPath())
                    .position(dto.getPosition())
                    .body(dto.getBody())
                    .userLogin(dto.getUser() == null ? null : dto.getUser().getLogin())
                    .createdAt(dto.getCreatedAt())
                    .pullRequest(pr)
                    .build());
        }
    }
}
