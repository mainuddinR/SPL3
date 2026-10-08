export type PatternConfidence = 'HIGH' | 'MEDIUM' | 'LOW';
export type PatternFamily = 'GOF' | 'ARCHITECTURAL';
export type PatternJobStatus = 'NOT_ANALYZED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type PatternConformanceStatus = 'CONFORMS' | 'POTENTIAL_DEVIATION' | 'NOT_APPLICABLE' | 'INSUFFICIENT_EVIDENCE';

export interface PatternEvidence {
  role: string;
  className: string;
  sourcePath: string;
  methodName: string | null;
  line: number | null;
}

export interface PatternOccurrence {
  id: number;
  patternType: string;
  patternFamily: PatternFamily;
  confidence: PatternConfidence;
  rationale: string;
  confidenceReason: string;
  evidence: PatternEvidence[];
}

export interface PatternSummary {
  patternType: string; occurrenceCount: number; highConfidenceCount: number; mediumConfidenceCount: number;
  participatingClassCount: number; packageCount: number; sourceRootCount: number | null; importanceScore: number;
  classification: 'PRIMARY' | 'SECONDARY' | 'LOCALIZED' | 'SUPPORTING';
}
export interface RepositoryDesignSummary {
  architecturalStyle: string; primaryGofPattern: string | null;
  primaryPatternStatus: 'DOMINANT' | 'MIXED' | 'LOCALIZED' | 'NO_GOF' | 'NO_JAVA';
  secondaryGofPatterns: string[]; mixedPatternUsage: boolean;
  patternSummaries: PatternSummary[]; architecturalConventionSummaries: PatternSummary[];
  rankingExplanation: string;
}

export interface PatternProfile {
  designSummary?: RepositoryDesignSummary | null;
  diagnostics?: { javaFileCount: number; typeCount: number; interfaceCount: number; abstractClassCount: number;
    relationshipDefinition?: string; controllerCount: number; serviceCount: number; repositoryCount: number; relationshipCount: number; detectedPatternCount: number } | null;
  failureCode?: string | null;
  observed?: number | null;
  limit?: number | null;
  id: number | null;
  projectId: number;
  status: PatternJobStatus;
  progress: string;
  analyzedCommitSha: string | null;
  currentDefaultBranchSha: string | null;
  stale: boolean | null;
  defaultBranch: string | null;
  analyzedAt: string | null;
  javaFileCount: number | null;
  failureMessage: string | null;
  occurrences: PatternOccurrence[];
}

export interface PatternConformanceFinding {
  patternType: string;
  patternFamily: PatternFamily;
  status: PatternConformanceStatus;
  confidence: PatternConfidence;
  file: string | null;
  line: number | null;
  className: string | null;
  rationale: string;
  recommendation: string | null;
  evidence: PatternEvidence[];
}

export interface PatternConformance {
  summary?: { overallStatus: PatternConformanceStatus; conforms: number; potentialDeviations: number;
    notApplicable: number; insufficientEvidence: number } | null;
  id: number;
  pullRequestId: number;
  profileId: number;
  status: PatternJobStatus;
  progress: string;
  failureMessage: string | null;
  checkedHeadSha: string | null;
  checkedBaseSha: string | null;
  checkedAt: string;
  findings: PatternConformanceFinding[];
}
