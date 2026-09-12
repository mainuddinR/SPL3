package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.SatdCandidateDTO;
import com.mainuddin.codereview.entity.PullRequestFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SatdExtractionServiceTest {

    private SatdExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new SatdExtractionService();
    }

    private PullRequestFile createFile(String filename, String patch) {
        PullRequestFile file = new PullRequestFile();
        file.setFilename(filename);
        file.setPatch(patch);
        return file;
    }

    @Test
    void testTodoComment() {
        String patch = "@@ -10,3 +10,4 @@\n" +
                       " public class Test {\n" +
                       "+    // TODO: implement this\n" +
                       "+    public void run() {}\n" +
                       " }\n";
        List<SatdCandidateDTO> candidates = extractionService.extractCandidates(createFile("Test.java", patch));
        assertEquals(1, candidates.size());
        assertEquals("// TODO: implement this", candidates.get(0).getCommentText());
        assertEquals(11, candidates.get(0).getLineNumber());
    }

    @Test
    void testNullOrEmptyPatch() {
        assertTrue(extractionService.extractCandidates(createFile("Test.java", null)).isEmpty());
        assertTrue(extractionService.extractCandidates(createFile("Test.java", "")).isEmpty());
        assertTrue(extractionService.extractCandidates(createFile("Test.java", "   ")).isEmpty());
    }

    @Test
    void testUnsupportedFile() {
        String patch = "@@ -1,1 +1,2 @@\n" +
                       "+ // TODO: add text\n";
        assertTrue(extractionService.extractCandidates(createFile("readme.txt", patch)).isEmpty());
    }

    @Test
    void testInlineComment() {
        String patch = "@@ -50,2 +50,3 @@\n" +
                       "     int a = 1;\n" +
                       "+    int b = 2; // FIXME: should be 3\n" +
                       "     int c = a + b;\n";
        List<SatdCandidateDTO> candidates = extractionService.extractCandidates(createFile("Math.java", patch));
        assertEquals(1, candidates.size());
        assertEquals("// FIXME: should be 3", candidates.get(0).getCommentText());
        assertTrue(candidates.get(0).getSurroundingCode().contains("int b = 2; // FIXME: should be 3"));
        assertEquals(51, candidates.get(0).getLineNumber());
    }

    @Test
    void testStringContainingComment() {
        String patch = "@@ -1,2 +1,3 @@\n" +
                       " public void url() {\n" +
                       "+    String url = \"http://example.com\";\n" +
                       " }\n";
        assertTrue(extractionService.extractCandidates(createFile("Test.java", patch)).isEmpty());
    }

    @Test
    void testMultiLineBlockComment() {
        String patch = "@@ -20,2 +20,6 @@\n" +
                       " public void test() {\n" +
                       "+    /*\n" +
                       "+     * TODO: this is a long\n" +
                       "+     * block comment\n" +
                       "+     */\n" +
                       " }\n";
        List<SatdCandidateDTO> candidates = extractionService.extractCandidates(createFile("Test.java", patch));
        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).getCommentText().contains("block comment"));
        assertEquals(21, candidates.get(0).getLineNumber());
    }

    @Test
    void testMultipleComments() {
        String patch = "@@ -1,3 +1,5 @@\n" +
                       " class Main {\n" +
                       "+    // HACK: workaround 1\n" +
                       "     int a;\n" +
                       "+    // TODO: implement 2\n" +
                       " }\n";
        List<SatdCandidateDTO> candidates = extractionService.extractCandidates(createFile("Main.java", patch));
        assertEquals(2, candidates.size());
        assertEquals("// HACK: workaround 1", candidates.get(0).getCommentText());
        assertEquals("// TODO: implement 2", candidates.get(1).getCommentText());
    }

    @Test
    void testDeletedLineIgnored() {
        String patch = "@@ -10,3 +10,3 @@\n" +
                       "     int a = 1;\n" +
                       "-    // old comment\n" +
                       "+    // new comment\n" +
                       "     int b = 2;\n";
        List<SatdCandidateDTO> candidates = extractionService.extractCandidates(createFile("Test.java", patch));
        assertEquals(1, candidates.size());
        assertEquals("// new comment", candidates.get(0).getCommentText());
        // Context should not contain deleted line
        assertFalse(candidates.get(0).getSurroundingCode().contains("old comment"));
    }
}
