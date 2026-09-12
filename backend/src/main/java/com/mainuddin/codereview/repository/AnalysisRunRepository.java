package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.AnalysisRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
    List<AnalysisRun> findByPullRequestId(Long pullRequestId);
}
