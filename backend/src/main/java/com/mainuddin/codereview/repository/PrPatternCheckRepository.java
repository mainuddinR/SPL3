package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.PrPatternCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PrPatternCheckRepository extends JpaRepository<PrPatternCheck, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from PrPatternCheck c where c.id = :id and c.pullRequest.id = :prId")
    Optional<PrPatternCheck> lockForAdvice(Long id, Long prId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "profile")
    Optional<PrPatternCheck> findFirstByPullRequestIdOrderByIdDesc(Long pullRequestId);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "profile")
    Optional<PrPatternCheck> findById(Long id);
}
