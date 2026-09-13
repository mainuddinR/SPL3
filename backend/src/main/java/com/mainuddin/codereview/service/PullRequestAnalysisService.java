package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.CodeBertRequestDTO;
import com.mainuddin.codereview.dto.CodeBertResponseDTO;
import com.mainuddin.codereview.dto.SatdAnalysisResponseDTO;
import com.mainuddin.codereview.dto.SatdCandidateDTO;
import com.mainuddin.codereview.entity.PullRequest;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.repository.PullRequestFileRepository;
import com.mainuddin.codereview.repository.PullRequestRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PullRequestAnalysisService {

    private final PullRequestRepository pullRequestRepository;
    private final PullRequestFileRepository pullRequestFileRepository;
    private final SatdExtractionService satdExtractionService;
    private final CodeBertClientService codeBertClientService;

    public PullRequestAnalysisService(
            PullRequestRepository pullRequestRepository,
            PullRequestFileRepository pullRequestFileRepository,
            SatdExtractionService satdExtractionService,
            CodeBertClientService codeBertClientService) {
        this.pullRequestRepository = pullRequestRepository;
        this.pullRequestFileRepository = pullRequestFileRepository;
        this.satdExtractionService = satdExtractionService;
        this.codeBertClientService = codeBertClientService;
    }

    public List<SatdAnalysisResponseDTO> analyzePullRequest(Long prId) {
        PullRequest pr = pullRequestRepository.findById(prId)
                .orElseThrow(() -> new RuntimeException("Pull request not found"));

        List<PullRequestFile> files = pullRequestFileRepository.findByPullRequestId(pr.getId());
        List<SatdAnalysisResponseDTO> analysisResults = new ArrayList<>();

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
                            .build();

                    analysisResults.add(responseDTO);
                }
            }
        }

        return analysisResults;
    }
}
