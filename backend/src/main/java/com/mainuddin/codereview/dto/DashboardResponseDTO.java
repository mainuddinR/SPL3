package com.mainuddin.codereview.dto;

import java.util.List;
import java.util.Map;

public record DashboardResponseDTO(DashboardStatsDTO stats, Map<String, Long> categoryDistribution,
                                   Map<String, Long> severityDistribution,
                                   List<RepositorySummaryDTO> repositories) {}
