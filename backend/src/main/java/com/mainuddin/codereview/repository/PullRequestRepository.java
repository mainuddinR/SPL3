package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    List<PullRequest> findByGithubProjectId(Long projectId);
    Optional<PullRequest> findByGithubPrId(Long githubPrId);
}
