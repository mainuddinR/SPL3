package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.GithubFileDTO;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.repository.PullRequestFileRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PullRequestFileSnapshotService {
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestFileRepository fileRepository;

    public PullRequestFileSnapshotService(PullRequestRepository pullRequestRepository, PullRequestFileRepository fileRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public void replace(Long prId, String headSha, String baseSha, List<GithubFileDTO> files) {
        PullRequest pr = pullRequestRepository.lockById(prId).orElseThrow();
        fileRepository.deleteByPullRequestId(prId);
        fileRepository.flush();
        for (GithubFileDTO file : files) {
            fileRepository.save(PullRequestFile.builder()
                    .sha(file.getSha()) // Git blob SHA, distinct from the PR head commit SHA.
                    .filename(file.getFilename())
                    .status(file.getStatus())
                    .additions(file.getAdditions())
                    .deletions(file.getDeletions())
                    .changes(file.getChanges())
                    .patch(file.getPatch())
                    .pullRequest(pr)
                    .build());
        }
        pr.setHeadSha(headSha);
        pr.setBaseSha(baseSha);
        pr.setSyncedFilesHeadSha(headSha);
        pr.setSyncedFilesBaseSha(baseSha);
        pullRequestRepository.save(pr);
    }
}
