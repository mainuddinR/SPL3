export interface SatdAnalysisResponse {
    filename: string;
    lineNumber: number;
    commentText: string;
    precedingCode: string;
    succeedingCode: string;
    label: 'SATD' | 'NON-SATD';
    satdProbability: number;
    nonSatdProbability: number;
    confidence: number;
    debtCategory: DebtCategory | null;
    categoryReason: string | null;
    categoryRuleVersion: string | null;
    severityState: 'ASSESSED' | 'NOT_ASSESSED' | 'NOT_APPLICABLE' | 'LEGACY';
    severityScore: number | null;
    severityReason: string | null;
    severityRuleVersion: string | null;
    methodLength: number | null;
    methodComplexity: number | null;
    methodMetricsRuleVersion: string | null;
    riskEvidence: string | null;
    riskEvidenceRuleVersion: string | null;
}

export type DebtCategory = 'DESIGN' | 'DEFECT' | 'TEST' | 'REQUIREMENT' | 'DOCUMENTATION' | 'UNCLASSIFIED' | 'NOT_ASSESSED';

export type AnalysisRunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface AnalysisRunSummary {
    id: number;
    status: AnalysisRunStatus;
    startedAt: string | null;
    completedAt: string | null;
    analyzedCandidateCount: number | null;
    satdFindingCount: number;
}

export interface SavedSatdFinding extends Omit<SatdAnalysisResponse, 'label'> {
    label: 'SATD';
    debtCategory: DebtCategory;
}

export interface AnalysisRunDetail {
    run: AnalysisRunSummary;
    findings: SavedSatdFinding[];
}

export interface PullRequest {
    id: number;
    githubPrId: number;
    number: number;
    title: string;
    state: string;
    htmlUrl: string;
    body: string;
    createdAt: string;
    updatedAt: string;
}

export interface PullRequestFile {
    id: number;
    sha: string;
    filename: string;
    status: string;
    additions: number;
    deletions: number;
    changes: number;
    patch: string;
}

export interface PullRequestComment {
    id: number;
    githubCommentId: number;
    path: string;
    position: number;
    body: string;
    userLogin: string;
    createdAt: string;
}
