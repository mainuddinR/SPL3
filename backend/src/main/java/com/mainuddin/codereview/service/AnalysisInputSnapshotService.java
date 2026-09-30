package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.repository.PullRequestFileRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisInputSnapshotService {
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestFileRepository fileRepository;

    public AnalysisInputSnapshotService(PullRequestRepository pullRequestRepository, PullRequestFileRepository fileRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.fileRepository = fileRepository;
    }

    public record Snapshot(PullRequest pullRequest, List<PullRequestFile> files) {}

    @Transactional
    public Snapshot capture(Long prId) {
        PullRequest pr = pullRequestRepository.lockById(prId).orElseThrow();
        pr.getGithubProject().getName(); // Materialize project identity before leaving the snapshot transaction.
        List<PullRequestFile> files = fileRepository.findByPullRequestId(prId).stream()
                .map(file -> PullRequestFile.builder()
                        .id(file.getId()).sha(file.getSha()).filename(file.getFilename())
                        .status(file.getStatus()).additions(file.getAdditions())
                        .deletions(file.getDeletions()).changes(file.getChanges())
                        .patch(file.getPatch()).build())
                .toList();
        return new Snapshot(pr, files);
    }
}
