package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.PullRequestFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PullRequestFileRepository extends JpaRepository<PullRequestFile, Long> {
    List<PullRequestFile> findByPullRequestId(Long pullRequestId);
    void deleteByPullRequestId(Long pullRequestId);
}
