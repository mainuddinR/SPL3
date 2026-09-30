package com.mainuddin.codereview.dto;

public record DashboardFindingSnapshotDTO(Long analysisRunId, String debtCategory, String categoryRuleVersion,
                                          Integer severityScore) {}
