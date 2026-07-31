package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.GithubProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GithubProjectRepository extends JpaRepository<GithubProject, Long> {
    List<GithubProject> findByUserId(Long userId);
    boolean existsByGithubRepoIdAndUserId(Long githubRepoId, Long userId);
}
