export interface DashboardStats {
  repositoryCount: number;
  pullRequestCount: number;
  analyzedPullRequestCount: number;
  currentSatdFindingCount: number;
  latestAnalysisAt: string | null;
}

export interface RepositoryAnalysisSummary {
  designProfileStatus?: string;
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

export interface DashboardPage<T> { items: T[]; total: number; page: number; size: number; }
export interface DashboardRepositoryRow {
  id: number; fullName: string; url: string; pullRequestCount: number;
  analyzedPullRequestCount: number; currentSatdFindingCount: number;
  lastCompletedAnalysisAt: string | null;
}
export interface DashboardPullRequestRow {
  id: number; repositoryId: number; repository: string; number: number; title: string;
  state: string; updatedAt: string | null; analyzed: boolean;
  latestCompletedRunId: number | null; latestCompletedAt: string | null;
  currentSatdFindingCount: number;
}
export interface DashboardAnalyzedRow {
  pullRequestId: number; repositoryId: number; repository: string; number: number;
  title: string; runId: number; analyzedCandidateCount: number | null;
  satdFindingCount: number; completedAt: string | null; analyzedHeadSha: string | null;
}
export interface DashboardFindingRow {
  id: number; repositoryId: number; repository: string; pullRequestId: number;
  pullRequestNumber: number; pullRequestTitle: string; runId: number;
  filename: string; lineNumber: number | null; commentText: string;
  satdProbability: number; confidence: number; debtCategory: string;
  categoryReason: string | null; severityScore: number | null;
  severityReason: string | null; aiSuggestionAvailable: boolean; completedAt: string | null;
}
import { SavedSatdFinding } from './pull-request.model';


export interface DashboardFindingDetail {
  repository: string; pullRequestId: number; pullRequestNumber: number;
  pullRequestTitle: string; runId: number; completedAt: string | null;
  finding: SavedSatdFinding;
}
