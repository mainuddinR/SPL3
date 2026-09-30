package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.CodeBertResponseDTO;
import com.mainuddin.codereview.entity.*;
import com.mainuddin.codereview.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:persistence_testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class PullRequestAnalysisPersistenceTest {
    @Autowired PullRequestAnalysisService analysisService;
    @Autowired UserRepository users;
    @Autowired GithubProjectRepository projects;
    @Autowired PullRequestRepository prs;
    @Autowired PullRequestFileRepository files;
    @Autowired AnalysisRunRepository runs;
    @Autowired SATDFindingRepository findings;
    @MockBean CodeBertClientService model;
    @SpyBean DebtCategoryAssessmentService categoryAssessor;
    @SpyBean GithubSourceRetrievalService sourceRetrieval;
    @Autowired MockMvc mvc;
    @Autowired AnalysisHistoryService historyService;

    private PullRequest pr;
    private PullRequestFile file;
    private String githubId;

    @BeforeEach
    void setUp() {
        githubId = UUID.randomUUID().toString();
        User user = users.save(User.builder().githubId(githubId).username("owner").build());
        GithubProject project = projects.save(GithubProject.builder()
                .githubRepoId(System.nanoTime()).name("repo").fullName("owner/repo")
                .htmlUrl("https://example.test/repo").ownerLogin("owner").user(user).build());
        pr = prs.save(PullRequest.builder().githubPrId(System.nanoTime()).number(1)
                .title("Test PR").state("open").githubProject(project)
                .headSha("current-head").baseSha("current-base")
                .syncedFilesHeadSha("files-head").syncedFilesBaseSha("files-base").build());
        file = files.save(PullRequestFile.builder().pullRequest(pr).filename("Main.java")
                .sha("file-blob-sha")
                .patch("@@ -1,2 +1,3 @@\n class Main {\n+ // TODO: fix this\n int x;\n")
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(githubId, null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
        reset(model);
    }

    private List<AnalysisRun> prRuns() {
        return runs.findByPullRequestId(pr.getId());
    }

    private void predict(String label) {
        when(model.analyzeCandidate(any())).thenReturn(CodeBertResponseDTO.builder()
                .label(label).satdProbability(0.9).nonSatdProbability(0.1).confidence(0.9).build());
    }

    @Test
    void satdSnapshotSurvivesFileReplacementAndRepeatedAnalysis() {
        predict("SATD");
        var response = analysisService.analyzePullRequest(pr.getId());
        assertEquals(1, response.size());
        assertEquals("SATD", response.get(0).getLabel());
        assertEquals("Main.java", response.get(0).getFilename());

        AnalysisRun first = prRuns().get(0);
        assertEquals(AnalysisRunStatus.COMPLETED, first.getStatus());
        assertEquals("files-head", first.getAnalyzedHeadSha());
        assertEquals("files-base", first.getAnalyzedBaseSha());
        assertNotEquals(file.getSha(), first.getAnalyzedHeadSha());
        assertNotNull(first.getStartedAt());
        assertNotNull(first.getCompletedAt());
        assertEquals(1, first.getAnalyzedCandidateCount());
        SATDFinding saved = findings.findByAnalysisRunId(first.getId()).get(0);
        assertEquals("Main.java", saved.getFilename());
        assertEquals(2, saved.getLineNumber());
        assertEquals("// TODO: fix this", saved.getCommentText());
        assertEquals("class Main {", saved.getPrecedingCode().trim());
        assertEquals("int x;", saved.getSucceedingCode().trim());
        assertEquals(0.9, saved.getSatdProbability());
        assertEquals(0.1, saved.getNonSatdProbability());
        assertEquals(0.9, saved.getConfidence());
        assertNull(saved.getDebtCategory());
        assertNull(saved.getSeverityScore());
        assertNull(saved.getSecurityFlag());
        assertNull(saved.getAiSuggestion());

        files.deleteById(file.getId());
        files.save(PullRequestFile.builder().pullRequest(pr).filename("Main.java")
                .patch("@@ -1,1 +1,1 @@\n+// different code\n").build());
        SATDFinding historical = findings.findById(saved.getId()).orElseThrow();
        assertEquals("// TODO: fix this", historical.getCommentText());
        assertEquals("class Main {", historical.getPrecedingCode().trim());

        analysisService.analyzePullRequest(pr.getId());
        assertEquals(2, prRuns().size());
        assertEquals(1, findings.findByAnalysisRunId(first.getId()).size());
    }

    @Test
    void nonSatdAndEmptyRunsHaveDistinctCounts() {
        predict("NON-SATD");
        assertEquals(1, analysisService.analyzePullRequest(pr.getId()).size());
        AnalysisRun nonSatdRun = prRuns().get(0);
        assertEquals(AnalysisRunStatus.COMPLETED, nonSatdRun.getStatus());
        assertEquals(1, nonSatdRun.getAnalyzedCandidateCount());
        assertTrue(findings.findByAnalysisRunId(nonSatdRun.getId()).isEmpty());

        files.deleteById(file.getId());
        assertTrue(analysisService.analyzePullRequest(pr.getId()).isEmpty());
        AnalysisRun emptyRun = prRuns().stream().filter(run -> !run.getId().equals(nonSatdRun.getId())).findFirst().orElseThrow();
        assertEquals(AnalysisRunStatus.COMPLETED, emptyRun.getStatus());
        assertEquals(0, emptyRun.getAnalyzedCandidateCount());
        assertTrue(findings.findByAnalysisRunId(emptyRun.getId()).isEmpty());
    }

    @Test
    void inferenceFailureLeavesFailedRunWithoutFindings() {
        file.setPatch("@@ -1,1 +1,4 @@\n+// TODO: first\n+int x;\n+// TODO: second\n class Main {}\n");
        files.save(file);
        when(model.analyzeCandidate(any()))
                .thenReturn(CodeBertResponseDTO.builder().label("SATD")
                        .satdProbability(0.9).nonSatdProbability(0.1).confidence(0.9).build())
                .thenThrow(new RuntimeException("ML unavailable"));
        assertThrows(RuntimeException.class, () -> analysisService.analyzePullRequest(pr.getId()));
        AnalysisRun run = prRuns().get(0);
        assertEquals(AnalysisRunStatus.FAILED, run.getStatus());
        assertNotNull(run.getCompletedAt());
        assertNull(run.getAnalyzedCandidateCount());
        assertTrue(findings.findByAnalysisRunId(run.getId()).isEmpty());
    }

    @Test
    void anotherUserCannotAnalyzeOrCreateRun() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone-else", null, List.of()));
        assertThrows(com.mainuddin.codereview.exception.ResourceNotFoundException.class,
                () -> analysisService.analyzePullRequest(pr.getId()));
        assertTrue(prRuns().isEmpty());
        verifyNoInteractions(model);
    }

    @Test
    void postKeepsExistingJsonArrayContract() throws Exception {
        predict("NON-SATD");
        mvc.perform(post("/api/prs/{prId}/analyze", pr.getId()).with(user(githubId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("Main.java"))
                .andExpect(jsonPath("$[0].lineNumber").value(2))
                .andExpect(jsonPath("$[0].commentText").value("// TODO: fix this"))
                .andExpect(jsonPath("$[0].label").value("NON-SATD"))
                .andExpect(jsonPath("$[0].satdProbability").value(0.9))
                .andExpect(jsonPath("$[0].nonSatdProbability").value(0.1))
                .andExpect(jsonPath("$[0].confidence").value(0.9));
        mvc.perform(post("/api/prs/{prId}/analyze", pr.getId()).with(user("someone-else")))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyListsSeparateRunsNewestFirstWithDistinctCountsAndStatuses() throws Exception {
        predict("NON-SATD");
        analysisService.analyzePullRequest(pr.getId());
        Long firstId = prRuns().get(0).getId();
        files.deleteById(file.getId());
        analysisService.analyzePullRequest(pr.getId());
        Long secondId = prRuns().stream().map(AnalysisRun::getId).filter(id -> !id.equals(firstId)).findFirst().orElseThrow();
        AnalysisRun failed = runs.save(AnalysisRun.builder().pullRequest(pr)
                .status(AnalysisRunStatus.FAILED).startedAt(java.time.LocalDateTime.now().plusMinutes(1)).build());

        mvc.perform(get("/api/prs/{prId}/analysis-runs", pr.getId()).with(user(githubId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(failed.getId()))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].analyzedCandidateCount").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(secondId))
                .andExpect(jsonPath("$[1].analyzedCandidateCount").value(0))
                .andExpect(jsonPath("$[2].id").value(firstId))
                .andExpect(jsonPath("$[2].analyzedCandidateCount").value(1))
                .andExpect(jsonPath("$[2].satdFindingCount").value(0));
    }

    @Test
    void historyDetailUsesSavedSnapshotAfterFileChanges() throws Exception {
        predict("SATD");
        analysisService.analyzePullRequest(pr.getId());
        Long runId = prRuns().get(0).getId();
        files.deleteById(file.getId());
        files.save(PullRequestFile.builder().pullRequest(pr).filename("Other.java")
                .patch("@@ -1,1 +1,1 @@\n+// different\n").build());

        mvc.perform(get("/api/prs/{prId}/analysis-runs/{runId}", pr.getId(), runId).with(user(githubId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("COMPLETED"))
                .andExpect(jsonPath("$.run.analyzedCandidateCount").value(1))
                .andExpect(jsonPath("$.run.satdFindingCount").value(1))
                .andExpect(jsonPath("$.findings[0].filename").value("Main.java"))
                .andExpect(jsonPath("$.findings[0].lineNumber").value(2))
                .andExpect(jsonPath("$.findings[0].commentText").value("// TODO: fix this"))
                .andExpect(jsonPath("$.findings[0].precedingCode").value("class Main {"))
                .andExpect(jsonPath("$.findings[0].succeedingCode").value("int x;"))
                .andExpect(jsonPath("$.findings[0].label").value("SATD"))
                .andExpect(jsonPath("$.findings[0].satdProbability").value(0.9))
                .andExpect(jsonPath("$.findings[0].nonSatdProbability").value(0.1))
                .andExpect(jsonPath("$.findings[0].confidence").value(0.9));
        verify(model, times(1)).analyzeCandidate(any());
    }

    @Test
    void historyRejectsOtherOwnersAndRunsFromAnotherPr() throws Exception {
        predict("SATD");
        analysisService.analyzePullRequest(pr.getId());
        Long runId = prRuns().get(0).getId();
        PullRequest otherPr = prs.save(PullRequest.builder().githubPrId(System.nanoTime()).number(2)
                .title("Other PR").state("open").githubProject(pr.getGithubProject()).build());

        mvc.perform(get("/api/prs/{prId}/analysis-runs", pr.getId()).with(user("someone-else")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/prs/{prId}/analysis-runs/{runId}", pr.getId(), runId).with(user("someone-else")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/prs/{prId}/analysis-runs/{runId}", otherPr.getId(), runId).with(user(githubId)))
                .andExpect(status().isNotFound());
        verify(model, times(1)).analyzeCandidate(any());
    }

    @Test
    void onlySatdIsAssessedAndLiveSavedHistoryUseSameCategory() throws Exception {
        file.setPatch("@@ -1,2 +1,3 @@\n class Main {\n+ // TODO: refactor this tightly coupled design\n int x;\n");
        files.save(file);
        predict("NON-SATD");
        var nonSatd = analysisService.analyzePullRequest(pr.getId()).get(0);
        assertNull(nonSatd.getDebtCategory());
        assertNull(nonSatd.getCategoryRuleVersion());
        verifyNoInteractions(categoryAssessor);

        predict("SATD");
        var live = analysisService.analyzePullRequest(pr.getId()).get(0);
        verify(categoryAssessor, times(1)).assess(any(), any(), any(), any());
        assertEquals("DESIGN", live.getDebtCategory());
        assertEquals(CategoryAssessment.RULE_VERSION, live.getCategoryRuleVersion());
        assertEquals(0.9, live.getSatdProbability());
        assertEquals(0.1, live.getNonSatdProbability());
        assertEquals(0.9, live.getConfidence());

        AnalysisRun satdRun = prRuns().stream().filter(run -> !findings.findByAnalysisRunId(run.getId()).isEmpty()).findFirst().orElseThrow();
        SATDFinding saved = findings.findByAnalysisRunId(satdRun.getId()).get(0);
        assertEquals(live.getDebtCategory(), saved.getDebtCategory());
        assertEquals(live.getCategoryReason(), saved.getCategoryReason());
        assertEquals(live.getCategoryRuleVersion(), saved.getCategoryRuleVersion());

        mvc.perform(get("/api/prs/{prId}/analysis-runs/{runId}", pr.getId(), satdRun.getId()).with(user(githubId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].debtCategory").value("DESIGN"))
                .andExpect(jsonPath("$.findings[0].categoryReason").value(live.getCategoryReason()))
                .andExpect(jsonPath("$.findings[0].categoryRuleVersion").value(CategoryAssessment.RULE_VERSION));
        verify(categoryAssessor, times(1)).assess(any(), any(), any(), any());
    }

    @Test
    void assessedUnclassifiedAndLegacyNullHaveDifferentHistoryRepresentations() throws Exception {
        predict("SATD");
        analysisService.analyzePullRequest(pr.getId());
        AnalysisRun assessedRun = prRuns().get(0);
        SATDFinding assessed = findings.findByAnalysisRunId(assessedRun.getId()).get(0);
        assertNull(assessed.getDebtCategory());
        assertNotNull(assessed.getCategoryReason());
        assertEquals(CategoryAssessment.RULE_VERSION, assessed.getCategoryRuleVersion());
        mvc.perform(get("/api/prs/{prId}/analysis-runs/{runId}", pr.getId(), assessedRun.getId()).with(user(githubId)))
                .andExpect(jsonPath("$.findings[0].debtCategory").value("UNCLASSIFIED"));

        AnalysisRun legacyRun = runs.save(AnalysisRun.builder().pullRequest(pr).status(AnalysisRunStatus.COMPLETED)
                .startedAt(java.time.LocalDateTime.now()).completedAt(java.time.LocalDateTime.now()).analyzedCandidateCount(1).build());
        findings.save(SATDFinding.builder().analysisRun(legacyRun).filename("Legacy.java").lineNumber(1)
                .commentText("// old debt").satdProbability(0.8).nonSatdProbability(0.2).confidence(0.8).build());
        mvc.perform(get("/api/prs/{prId}/analysis-runs/{runId}", pr.getId(), legacyRun.getId()).with(user(githubId)))
                .andExpect(jsonPath("$.findings[0].debtCategory").value("NOT_ASSESSED"))
                .andExpect(jsonPath("$.findings[0].categoryRuleVersion").doesNotExist());
    }

    @Test
    void assessorFailurePreservesBinaryResultAndPersistsNotAssessed() {
        predict("SATD");
        doThrow(new IllegalStateException("assessor failed")).when(categoryAssessor).assess(any(), any(), any(), any());
        var live = analysisService.analyzePullRequest(pr.getId()).get(0);
        assertEquals("SATD", live.getLabel());
        assertEquals(0.9, live.getSatdProbability());
        assertNull(live.getDebtCategory());
        assertNull(live.getCategoryRuleVersion());
        var saved = findings.findByAnalysisRunId(prRuns().get(0).getId()).get(0);
        assertNull(saved.getDebtCategory());
        assertNull(saved.getCategoryRuleVersion());
    }

    @Test
    void repeatedAnalysisKeepsEarlierCategorySnapshot() {
        predict("SATD");
        file.setPatch("@@ -1,2 +1,3 @@\n class Main {\n+ // TODO: refactor this design\n int x;\n");
        files.save(file);
        analysisService.analyzePullRequest(pr.getId());
        AnalysisRun firstRun = prRuns().get(0);
        assertEquals("DESIGN", findings.findByAnalysisRunId(firstRun.getId()).get(0).getDebtCategory());

        file.setPatch("@@ -1,2 +1,3 @@\n class Main {\n+ // TODO: outdated API documentation\n int x;\n");
        files.save(file);
        analysisService.analyzePullRequest(pr.getId());
        AnalysisRun secondRun = prRuns().stream().filter(run -> !run.getId().equals(firstRun.getId())).findFirst().orElseThrow();
        assertEquals("DOCUMENTATION", findings.findByAnalysisRunId(secondRun.getId()).get(0).getDebtCategory());
        assertEquals("DESIGN", findings.findByAnalysisRunId(firstRun.getId()).get(0).getDebtCategory());
    }

    @Test
    void laterPrRevisionDoesNotMutateEarlierRunSnapshot() {
        predict("NON-SATD");
        analysisService.analyzePullRequest(pr.getId());
        Long runId = prRuns().get(0).getId();
        pr.setHeadSha("new-head");
        pr.setBaseSha("new-base");
        pr.setSyncedFilesHeadSha("new-files-head");
        pr.setSyncedFilesBaseSha("new-files-base");
        prs.saveAndFlush(pr);
        assertEquals("files-head", runs.findById(runId).orElseThrow().getAnalyzedHeadSha());
        assertEquals("files-base", runs.findById(runId).orElseThrow().getAnalyzedBaseSha());
    }

    @Test
    void verifiedSatdSeverityIsIdenticalInLiveSavedAndHistoryAndRemainsImmutable() throws Exception {
        String source = "class Main {\n void m() {\n // TODO: missing authentication\n int x = 1;\n }\n}";
        file.setPatch("@@ -1,5 +1,6 @@\n class Main {\n+ void m() {\n+ // TODO: missing authentication\n+ int x = 1;\n+ }\n }");
        files.save(file);
        GithubSourceRetrievalService.Session session = mock(GithubSourceRetrievalService.Session.class);
        doReturn(session).when(sourceRetrieval).newSession();
        when(session.forCandidate(any(), any(), any(), any()))
                .thenReturn(new VerifiedSourceResult(VerifiedSourceResult.Status.VERIFIED, source));
        predict("SATD");

        var live = analysisService.analyzePullRequest(pr.getId()).get(0);
        assertEquals("SATD", live.getLabel());
        assertEquals("ASSESSED", live.getSeverityState());
        assertEquals(3, live.getSeverityScore());
        assertEquals(1, live.getMethodLength());
        assertEquals(1, live.getMethodComplexity());
        assertNotNull(live.getRiskEvidence());
        Long runId = prRuns().get(0).getId();
        SATDFinding saved = findings.findByAnalysisRunId(runId).get(0);
        assertEquals(live.getSeverityScore(), saved.getSeverityScore());
        assertEquals(live.getSeverityReason(), saved.getSeverityReason());
        assertEquals(live.getSeverityRuleVersion(), saved.getSeverityRuleVersion());
        assertEquals(live.getRiskEvidence(), saved.getRiskEvidence());
        mvc.perform(get("/api/prs/{prId}/analysis-runs/{runId}", pr.getId(), runId).with(user(githubId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].severityState").value("ASSESSED"))
                .andExpect(jsonPath("$.findings[0].severityScore").value(3))
                .andExpect(jsonPath("$.findings[0].methodLength").value(1));
        verify(session, times(1)).forCandidate(any(), any(), any(), any());
        files.deleteById(file.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(githubId, null, List.of()));
        assertEquals(3, historyService.detail(pr.getId(), runId).findings().get(0).severityScore());
        verify(session, times(1)).forCandidate(any(), any(), any(), any());
    }

    @Test
    void nonSatdNeverRequestsSourceAndUnavailableSatdStillPersists() {
        predict("NON-SATD");
        var nonSatd = analysisService.analyzePullRequest(pr.getId()).get(0);
        assertEquals("NOT_APPLICABLE", nonSatd.getSeverityState());
        verify(sourceRetrieval, never()).newSession();

        predict("SATD");
        var satd = analysisService.analyzePullRequest(pr.getId()).get(0);
        assertEquals("SATD", satd.getLabel());
        assertEquals("NOT_ASSESSED", satd.getSeverityState());
        assertNull(satd.getSeverityScore());
        SATDFinding saved = findings.findByAnalysisRunId(prRuns().stream()
                .filter(run -> !findings.findByAnalysisRunId(run.getId()).isEmpty()).findFirst().orElseThrow().getId()).get(0);
        assertEquals(SeverityAssessment.RULE_VERSION, saved.getSeverityRuleVersion());
        assertNull(saved.getSeverityScore());
    }

    @Test
    void sourceAndParserFailuresPreserveSatdAndCategoryInEveryRun() {
        file.setPatch("@@ -1,2 +1,3 @@\n class Main {\n+ // TODO: refactor this design\n int x;\n");
        files.save(file);
        predict("SATD");
        GithubSourceRetrievalService.Session session = mock(GithubSourceRetrievalService.Session.class);
        doReturn(session).when(sourceRetrieval).newSession();
        List<VerifiedSourceResult> unavailable = List.of(
                VerifiedSourceResult.of(VerifiedSourceResult.Status.UNAVAILABLE),
                VerifiedSourceResult.of(VerifiedSourceResult.Status.SHA_MISMATCH),
                VerifiedSourceResult.of(VerifiedSourceResult.Status.LOCATION_MISMATCH),
                VerifiedSourceResult.of(VerifiedSourceResult.Status.UNSUPPORTED),
                new VerifiedSourceResult(VerifiedSourceResult.Status.VERIFIED, "class Main { void m( {"),
                new VerifiedSourceResult(VerifiedSourceResult.Status.VERIFIED, "class Main {\n // TODO: refactor this design\n int x;\n}")
        );
        for (VerifiedSourceResult result : unavailable) {
            when(session.forCandidate(any(), any(), any(), any())).thenReturn(result);
            var live = analysisService.analyzePullRequest(pr.getId()).get(0);
            assertEquals("SATD", live.getLabel());
            assertEquals("DESIGN", live.getDebtCategory());
            assertEquals("NOT_ASSESSED", live.getSeverityState());
            assertNull(live.getSeverityScore());
            AnalysisRun latest = prRuns().stream().max(java.util.Comparator.comparing(AnalysisRun::getId)).orElseThrow();
            assertEquals(AnalysisRunStatus.COMPLETED, latest.getStatus());
            SATDFinding saved = findings.findByAnalysisRunId(latest.getId()).get(0);
            assertEquals("DESIGN", saved.getDebtCategory());
            assertNull(saved.getSeverityScore());
            assertEquals(SeverityAssessment.RULE_VERSION, saved.getSeverityRuleVersion());
        }
    }
}
