export interface DashboardStats {
  repositoryCount: number;
  pullRequestCount: number;
  analyzedPullRequestCount: number;
  currentSatdFindingCount: number;
  latestAnalysisAt: string | null;
}

export interface RepositoryAnalysisSummary {
  id: number;
  name: string;
  url: string;
  pullRequestCount: number;
  analyzedPullRequestCount: number;
  currentSatdFindingCount: number;
  lastCompletedAnalysisAt: string | null;
}

export interface DashboardResponse {
  stats: DashboardStats;
  categoryDistribution: Record<string, number>;
  severityDistribution: Record<string, number>;
  repositories: RepositoryAnalysisSummary[];
}
