package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.PatternOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PatternOccurrenceRepository extends JpaRepository<PatternOccurrence, Long> {
    long countByProfileId(Long profileId);
    List<PatternOccurrence> findByProfileIdOrderByIdAsc(Long profileId);
}
