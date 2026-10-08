package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.RepositoryPatternProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RepositoryPatternProfileRepository extends JpaRepository<RepositoryPatternProfile, Long> {
    Optional<RepositoryPatternProfile> findFirstByProjectIdOrderByIdDesc(Long projectId);
    Optional<RepositoryPatternProfile> findFirstByProjectIdAndStatusOrderByIdDesc(Long projectId, String status);
}
