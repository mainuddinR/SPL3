package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.RepositoryPatternProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RepositoryPatternProfileRepository extends JpaRepository<RepositoryPatternProfile, Long> {
    // An immutable PR-base profile has a commit SHA and no default branch. Keep it out of repository latest views.
    @org.springframework.data.jpa.repository.Query("select p from RepositoryPatternProfile p where p.project.id = :projectId and (p.defaultBranch is not null or p.analyzedCommitSha is null) order by p.id desc")
    java.util.List<RepositoryPatternProfile> findDefaultProfiles(Long projectId, org.springframework.data.domain.Pageable page);
    Optional<RepositoryPatternProfile> findFirstByProjectIdAndAnalyzedCommitShaAndStatusOrderByIdDesc(Long projectId, String sha, String status);
    java.util.List<RepositoryPatternProfile> findByProjectIdAndStatus(Long projectId, String status);
    Optional<RepositoryPatternProfile> findFirstByProjectIdOrderByIdDesc(Long projectId);
    Optional<RepositoryPatternProfile> findFirstByProjectIdAndStatusOrderByIdDesc(Long projectId, String status);
}
