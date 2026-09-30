package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.CodeBertRequestDTO;
import com.mainuddin.codereview.dto.CodeBertResponseDTO;
import com.mainuddin.codereview.dto.SatdAnalysisResponseDTO;
import com.mainuddin.codereview.dto.SatdCandidateDTO;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.entity.SATDFinding;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.PullRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class PullRequestAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(PullRequestAnalysisService.class);

    private final PullRequestRepository pullRequestRepository;
    private final SatdExtractionService satdExtractionService;
    private final CodeBertClientService codeBertClientService;
    private final AnalysisRunPersistenceService runPersistenceService;
    private final DebtCategoryAssessmentService categoryAssessmentService;
    private final AnalysisInputSnapshotService inputSnapshotService;
    private final GithubSourceRetrievalService sourceRetrievalService;
    private final JavaMethodMetricsService methodMetricsService;
    private final RiskEvidenceService riskEvidenceService;
    private final SeverityAssessmentService severityAssessmentService;

    public PullRequestAnalysisService(
            PullRequestRepository pullRequestRepository,
            SatdExtractionService satdExtractionService,
            CodeBertClientService codeBertClientService,
            AnalysisRunPersistenceService runPersistenceService,
            DebtCategoryAssessmentService categoryAssessmentService,
            AnalysisInputSnapshotService inputSnapshotService,
            GithubSourceRetrievalService sourceRetrievalService,
            JavaMethodMetricsService methodMetricsService,
            RiskEvidenceService riskEvidenceService,
            SeverityAssessmentService severityAssessmentService) {
        this.pullRequestRepository = pullRequestRepository;
        this.satdExtractionService = satdExtractionService;
        this.codeBertClientService = codeBertClientService;
        this.runPersistenceService = runPersistenceService;
        this.categoryAssessmentService = categoryAssessmentService;
        this.inputSnapshotService = inputSnapshotService;
        this.sourceRetrievalService = sourceRetrievalService;
        this.methodMetricsService = methodMetricsService;
        this.riskEvidenceService = riskEvidenceService;
        this.severityAssessmentService = severityAssessmentService;
    }

    public List<SatdAnalysisResponseDTO> analyzePullRequest(Long prId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("Pull request not found");
        }
        PullRequest pr = pullRequestRepository.findByIdAndGithubProjectUserGithubId(prId, authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Pull request not found"));

        AnalysisInputSnapshotService.Snapshot input = inputSnapshotService.capture(pr.getId());
        Long runId = runPersistenceService.start(input.pullRequest());
        try {
            List<PullRequestFile> files = input.files();
            List<SatdAnalysisResponseDTO> analysisResults = new ArrayList<>();
            List<SATDFinding> findings = new ArrayList<>();
            GithubSourceRetrievalService.Session sourceSession = null;

            for (PullRequestFile file : files) {
                if (file.getFilename() != null && file.getFilename().endsWith(".java") && file.getPatch() != null) {
                    List<SatdCandidateDTO> candidates = satdExtractionService.extractCandidates(file);
                
                    for (SatdCandidateDTO candidate : candidates) {
                        CodeBertRequestDTO request = CodeBertRequestDTO.builder()
                                .comment(candidate.getCommentText())
                                .precedingCode(candidate.getPrecedingCode())
                                .succeedingCode(candidate.getSucceedingCode())
                                .build();

                        CodeBertResponseDTO mlResponse = codeBertClientService.analyzeCandidate(request);

                        CategoryAssessment category = CategoryAssessment.notAssessed();
                        SeverityAssessment severity = SeverityAssessment.notApplicable();
                        if ("SATD".equals(mlResponse.getLabel())) {
                            try {
                                category = categoryAssessmentService.assess(candidate.getCommentText(),
                                        candidate.getFilename(), candidate.getPrecedingCode(), candidate.getSucceedingCode());
                            } catch (RuntimeException categoryFailure) {
                                log.warn("Category assessment unavailable for a SATD candidate", categoryFailure);
                            }
                            try {
                                if (sourceSession == null) sourceSession = sourceRetrievalService.newSession();
                                severity = assessSeverity(input, file, candidate, sourceSession);
                            } catch (RuntimeException optionalFailure) {
                                log.warn("Optional severity session unavailable for a SATD candidate", optionalFailure);
                                severity = SeverityAssessment.notAssessed("Severity evidence is unavailable");
                            }
                        }

                        SatdAnalysisResponseDTO responseDTO = SatdAnalysisResponseDTO.builder()
                                .filename(candidate.getFilename())
                                .lineNumber(candidate.getLineNumber())
                                .commentText(candidate.getCommentText())
                                .precedingCode(candidate.getPrecedingCode())
                                .succeedingCode(candidate.getSucceedingCode())
                                .label(mlResponse.getLabel())
                                .satdProbability(mlResponse.getSatdProbability())
                                .nonSatdProbability(mlResponse.getNonSatdProbability())
                                .confidence(mlResponse.getConfidence())
                                .debtCategory(category.category())
                                .categoryReason(category.reason())
                                .categoryRuleVersion(category.ruleVersion())
                                .severityState(severity.state())
                                .severityScore(severity.score())
                                .severityReason(severity.reason())
                                .severityRuleVersion(severity.ruleVersion())
                                .methodLength(severity.methodLength())
                                .methodComplexity(severity.complexity())
                                .methodMetricsRuleVersion(severity.methodMetricsRuleVersion())
                                .riskEvidence(severity.riskEvidence())
                                .riskEvidenceRuleVersion(severity.riskEvidenceRuleVersion())
                                .build();

                        analysisResults.add(responseDTO);
                        if ("SATD".equals(mlResponse.getLabel())) {
                            findings.add(SATDFinding.builder()
                                    .filename(candidate.getFilename())
                                    .lineNumber(candidate.getLineNumber())
                                    .commentText(candidate.getCommentText())
                                    .precedingCode(candidate.getPrecedingCode())
                                    .succeedingCode(candidate.getSucceedingCode())
                                    .satdProbability(mlResponse.getSatdProbability())
                                    .nonSatdProbability(mlResponse.getNonSatdProbability())
                                    .confidence(mlResponse.getConfidence())
                                    .debtCategory("UNCLASSIFIED".equals(category.category()) ? null : category.category())
                                    .categoryReason(category.reason())
                                    .categoryRuleVersion(category.ruleVersion())
                                    .severityScore(severity.score())
                                    .severityReason(severity.reason())
                                    .severityRuleVersion(severity.ruleVersion())
                                    .methodLength(severity.methodLength())
                                    .methodComplexity(severity.complexity())
                                    .methodMetricsRuleVersion(severity.methodMetricsRuleVersion())
                                    .riskEvidence(severity.riskEvidence())
                                    .riskEvidenceRuleVersion(severity.riskEvidenceRuleVersion())
                                    .detectedAt(LocalDateTime.now())
                                    .build());
                        }
                    }
                }
            }

            runPersistenceService.complete(runId, analysisResults.size(), findings);
            return analysisResults;
        } catch (RuntimeException failure) {
            try {
                runPersistenceService.fail(runId);
            } catch (RuntimeException statusFailure) {
                failure.addSuppressed(statusFailure);
            }
            throw failure;
        }
    }

    private SeverityAssessment assessSeverity(AnalysisInputSnapshotService.Snapshot input, PullRequestFile file,
                                               SatdCandidateDTO candidate, GithubSourceRetrievalService.Session session) {
        if (input.pullRequest().getSyncedFilesHeadSha() == null) {
            return SeverityAssessment.notAssessed("Analyzed revision is unavailable");
        }
        try {
            VerifiedSourceResult source = session.forCandidate(input.pullRequest().getGithubProject(),
                    input.pullRequest().getSyncedFilesHeadSha(), file, candidate);
            if (source.status() != VerifiedSourceResult.Status.VERIFIED) {
                return SeverityAssessment.notAssessed("Verified source or candidate location is unavailable");
            }
            MethodMetricsResult metrics = methodMetricsService.analyze(source, candidate.getLineNumber());
            if (metrics.status() != MethodMetricsResult.Status.AVAILABLE) {
                return SeverityAssessment.notAssessed("Containing method metrics are unavailable");
            }
            RiskEvidence risk = riskEvidenceService.assess(candidate.getCommentText());
            return severityAssessmentService.assess(metrics, risk);
        } catch (RuntimeException optionalFailure) {
            log.warn("Optional severity assessment unavailable for a SATD candidate", optionalFailure);
            return SeverityAssessment.notAssessed("Severity evidence is unavailable");
        }
    }
}
