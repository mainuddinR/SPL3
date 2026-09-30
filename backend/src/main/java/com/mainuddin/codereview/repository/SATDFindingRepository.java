package com.mainuddin.codereview.repository;

import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.dto.DashboardFindingSnapshotDTO;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SATDFindingRepository extends JpaRepository<SATDFinding, Long> {
    List<SATDFinding> findByAnalysisRunId(Long analysisRunId);
    long countByAnalysisRunId(Long analysisRunId);

    @Query("""
            select new com.mainuddin.codereview.dto.DashboardFindingSnapshotDTO(
              finding.analysisRun.id, finding.debtCategory, finding.categoryRuleVersion,
              finding.severityScore)
            from SATDFinding finding where finding.analysisRun.id in :runIds
            """)
    List<DashboardFindingSnapshotDTO> findDashboardSnapshots(@Param("runIds") List<Long> runIds);
}
