package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.SATDFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SATDFindingRepository extends JpaRepository<SATDFinding, Long> {
    List<SATDFinding> findByAnalysisRunId(Long analysisRunId);
    List<SATDFinding> findByPullRequestFileId(Long pullRequestFileId);
}
