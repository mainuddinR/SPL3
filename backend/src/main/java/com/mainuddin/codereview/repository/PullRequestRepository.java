package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    List<PullRequest> findByGithubProjectId(Long projectId);
    List<PullRequest> findByGithubProjectIdIn(List<Long> projectIds);
    Optional<PullRequest> findByGithubPrId(Long githubPrId);
    Optional<PullRequest> findByIdAndGithubProjectUserGithubId(Long id, String githubId);
    @Query("select pr from PullRequest pr join fetch pr.githubProject where pr.id = :id")
    Optional<PullRequest> findWithProjectById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pr from PullRequest pr where pr.id = :id")
    Optional<PullRequest> lockById(@Param("id") Long id);
}
