package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.dto.DashboardFindingSnapshotDTO;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface SATDFindingRepository extends JpaRepository<SATDFinding, Long> {
    List<SATDFinding> findByAnalysisRunId(Long analysisRunId);
    long countByAnalysisRunId(Long analysisRunId);
    Optional<SATDFinding> findByIdAndAnalysisRunId(Long id, Long analysisRunId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update SATDFinding finding set finding.aiSuggestion = :suggestion,
              finding.aiSuggestionGeneratedAt = :generatedAt,
              finding.aiSuggestionProviderModel = :providerModel,
              finding.aiSuggestionPromptVersion = :promptVersion
            where finding.id = :findingId and finding.analysisRun.id = :runId
              and finding.aiSuggestion is null
            """)
    int saveSuggestionIfAbsent(@Param("findingId") Long findingId, @Param("runId") Long runId,
                               @Param("suggestion") String suggestion, @Param("generatedAt") LocalDateTime generatedAt,
                               @Param("providerModel") String providerModel, @Param("promptVersion") String promptVersion);

    @Query("""
            select new com.mainuddin.codereview.dto.DashboardFindingSnapshotDTO(
              finding.analysisRun.id, finding.debtCategory, finding.categoryRuleVersion,
              finding.severityScore)
            from SATDFinding finding where finding.analysisRun.id in :runIds
            """)
    List<DashboardFindingSnapshotDTO> findDashboardSnapshots(@Param("runIds") List<Long> runIds);
}
