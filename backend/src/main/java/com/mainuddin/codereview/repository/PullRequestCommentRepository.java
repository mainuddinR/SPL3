package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.PullRequestComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PullRequestCommentRepository extends JpaRepository<PullRequestComment, Long> {
    List<PullRequestComment> findByPullRequestId(Long pullRequestId);
    void deleteByPullRequestId(Long pullRequestId);
}
