package com.mainuddin.codereview.service;

import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.SATDFindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class SuggestionPersistenceService {
    private final SATDFindingRepository findings;
    public SuggestionPersistenceService(SATDFindingRepository findings) { this.findings = findings; }

    @Transactional
    public SATDFinding saveOrGet(Long findingId, Long runId, String suggestion, String model, String version) {
        findings.saveSuggestionIfAbsent(findingId, runId, suggestion, LocalDateTime.now(), model, version);
        return findings.findByIdAndAnalysisRunId(findingId, runId)
                .orElseThrow(() -> new ResourceNotFoundException("Finding not found"));
    }
}
