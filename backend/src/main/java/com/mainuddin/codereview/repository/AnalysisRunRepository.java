package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.AnalysisRun;
import com.mainuddin.codereview.entity.AnalysisRunStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {
    List<AnalysisRun> findByPullRequestId(Long pullRequestId);
    List<AnalysisRun> findByPullRequestIdOrderByStartedAtDescIdDesc(Long pullRequestId);
    Optional<AnalysisRun> findByIdAndPullRequestId(Long id, Long pullRequestId);

    @Query("""
            select run from AnalysisRun run
            join fetch run.pullRequest pr
            join fetch pr.githubProject project
            where project.user.githubId = :githubId and run.status = :completed
              and not exists (
                select newer.id from AnalysisRun newer
                where newer.pullRequest = pr and newer.status = :completed
                  and (newer.completedAt > run.completedAt
                    or (run.completedAt is null and newer.completedAt is not null)
                    or ((newer.completedAt = run.completedAt
                         or (newer.completedAt is null and run.completedAt is null))
                        and newer.id > run.id))
              )
            """)
    List<AnalysisRun> findLatestCompletedForOwner(@Param("githubId") String githubId,
                                                   @Param("completed") AnalysisRunStatus completed);
}
