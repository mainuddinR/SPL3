package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.GithubProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

@Repository
public interface GithubProjectRepository extends JpaRepository<GithubProject, Long> {
    List<GithubProject> findByUserId(Long userId);
    List<GithubProject> findByUserGithubIdOrderByIdAsc(String githubId);
    Optional<GithubProject> findByIdAndUserGithubId(Long id, String githubId);
    boolean existsByGithubRepoIdAndUserId(Long githubRepoId, Long userId);
    Optional<GithubProject> findByGithubRepoIdAndUserId(Long githubRepoId, Long userId);
    long countByUserGithubId(String githubId);
    @Query("select project from GithubProject project join fetch project.user order by project.id")
    List<GithubProject> findAllWithUser();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from GithubProject project where project.id = :id")
    Optional<GithubProject> lockById(Long id);
}
