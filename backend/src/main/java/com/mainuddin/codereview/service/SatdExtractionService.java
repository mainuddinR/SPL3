package com.mainuddin.codereview.service;

import com.mainuddin.codereview.dto.SatdCandidateDTO;
import com.mainuddin.codereview.entity.PullRequestFile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SatdExtractionService {

    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("@@ -\\d+(,\\d+)? \\+(\\d+)(,\\d+)? @@.*");

    private static class SourceLine {
        String content;
        int lineNumber;
        boolean isAdded;
        int listIndex;

        SourceLine(String content, int lineNumber, boolean isAdded, int listIndex) {
            this.content = content;
            this.lineNumber = lineNumber;
            this.isAdded = isAdded;
            this.listIndex = listIndex;
        }
    }

    public List<SatdCandidateDTO> extractCandidates(PullRequestFile file) {
        List<SatdCandidateDTO> candidates = new ArrayList<>();

        if (file == null || file.getPatch() == null || file.getPatch().trim().isEmpty() || file.getFilename() == null) {
            return candidates;
        }
        
        if (!file.getFilename().endsWith(".java")) {
            return candidates;
        }

        String[] patchLines = file.getPatch().split("\n");
        
        List<SourceLine> currentHunkLines = new ArrayList<>();
        int currentNewLineNumber = 0;
        boolean inHunk = false;

        for (String line : patchLines) {
            Matcher matcher = HUNK_HEADER_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (inHunk && !currentHunkLines.isEmpty()) {
                    candidates.addAll(extractFromHunk(currentHunkLines, file.getFilename()));
                    currentHunkLines.clear();
                }
                currentNewLineNumber = Integer.parseInt(matcher.group(2));
                inHunk = true;
                continue;
            }

            if (inHunk) {
                if (line.startsWith("-")) {
                    // Do nothing, deleted line
                } else if (line.startsWith("+")) {
                    currentHunkLines.add(new SourceLine(line.substring(1), currentNewLineNumber, true, currentHunkLines.size()));
                    currentNewLineNumber++;
                } else if (line.startsWith(" ") || line.isEmpty()) {
                    String content = line.isEmpty() ? "" : line.substring(1);
                    currentHunkLines.add(new SourceLine(content, currentNewLineNumber, false, currentHunkLines.size()));
                    currentNewLineNumber++;
                }
            }
        }
        
        if (inHunk && !currentHunkLines.isEmpty()) {
            candidates.addAll(extractFromHunk(currentHunkLines, file.getFilename()));
        }

        return candidates;
    }
    
    private List<SatdCandidateDTO> extractFromHunk(List<SourceLine> hunkLines, String filename) {
        List<SatdCandidateDTO> candidates = new ArrayList<>();
        
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escapeNext = false;
        
        StringBuilder currentComment = new StringBuilder();
        int commentStartLineNumber = -1;
        int commentStartIndex = -1;
        boolean hasAddedLine = false;
        
        // For merging consecutive line comments
        StringBuilder pendingLineComment = new StringBuilder();
        int[] pendingLineCommentStartLineNum = {-1};
        int[] pendingLineCommentStartIndex = {-1};
        int[] pendingLineCommentEndIndex = {-1};
        boolean[] pendingHasAddedLine = {false};
        
        Runnable flushPendingLineComment = () -> {
            if (pendingLineComment.length() > 0 && pendingHasAddedLine[0]) {
                candidates.add(createCandidate(pendingLineComment.toString(), pendingLineCommentStartLineNum[0], pendingLineCommentStartIndex[0], pendingLineCommentEndIndex[0], hunkLines, filename));
            }
            pendingLineComment.setLength(0);
            pendingHasAddedLine[0] = false;
        };

        for (int i = 0; i < hunkLines.size(); i++) {
            SourceLine sl = hunkLines.get(i);
            String content = sl.content;
            
            boolean inLineComment = false;
            
            // Check if this line continues a previous line comment block.
            // A simple heuristic: if it only contains whitespace before a //
            boolean isOnlyWhitespaceBeforeLineComment = content.trim().startsWith("//");
            if (!isOnlyWhitespaceBeforeLineComment) {
                 flushPendingLineComment.run();
            }

            for (int j = 0; j < content.length(); j++) {
                char c = content.charAt(j);
                char nextC = (j + 1 < content.length()) ? content.charAt(j + 1) : '\0';
                
                if (escapeNext) {
                    escapeNext = false;
                    if (inBlockComment) {
                        currentComment.append(c);
                    } else if (inLineComment) {
                        currentComment.append(c);
                    }
                    continue;
                }
                
                if (inString) {
                    if (c == '\\') {
                        escapeNext = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                
                if (inChar) {
                    if (c == '\\') {
                        escapeNext = true;
                    } else if (c == '\'') {
                        inChar = false;
                    }
                    continue;
                }
                
                if (inBlockComment) {
                    if (c == '*' && nextC == '/') {
                        inBlockComment = false;
                        currentComment.append("*/");
                        j++;
                        
                        if (sl.isAdded) hasAddedLine = true;
                        
                        if (hasAddedLine) {
                            candidates.add(createCandidate(currentComment.toString(), commentStartLineNumber, commentStartIndex, i, hunkLines, filename));
                        }
                        currentComment.setLength(0);
                        commentStartLineNumber = -1;
                        commentStartIndex = -1;
                        hasAddedLine = false;
                    } else {
                        currentComment.append(c);
                    }
                    continue;
                }
                
                if (inLineComment) {
                    currentComment.append(c);
                    continue;
                }
                
                if (c == '"') {
                    inString = true;
                } else if (c == '\'') {
                    inChar = true;
                } else if (c == '/' && nextC == '/') {
                    inLineComment = true;
                    if (commentStartLineNumber == -1) {
                        commentStartLineNumber = sl.lineNumber;
                        commentStartIndex = sl.listIndex;
                    }
                    currentComment.append("//");
                    j++;
                } else if (c == '/' && nextC == '*') {
                    inBlockComment = true;
                    if (commentStartLineNumber == -1) {
                        commentStartLineNumber = sl.lineNumber;
                        commentStartIndex = sl.listIndex;
                    }
                    currentComment.append("/*");
                    j++;
                }
            }
            
            if (inLineComment) {
                if (sl.isAdded) hasAddedLine = true;
                if (pendingLineComment.length() == 0) {
                    pendingLineCommentStartLineNum[0] = commentStartLineNumber;
                    pendingLineCommentStartIndex[0] = commentStartIndex;
                } else {
                    pendingLineComment.append("\n");
                }
                pendingLineComment.append(currentComment.toString());
                pendingLineCommentEndIndex[0] = i;
                if (hasAddedLine) {
                    pendingHasAddedLine[0] = true;
                }
                
                currentComment.setLength(0);
                commentStartLineNumber = -1;
                commentStartIndex = -1;
                hasAddedLine = false;
            } else if (inBlockComment) {
                currentComment.append("\n");
                if (sl.isAdded) hasAddedLine = true;
            }
        }
        
        flushPendingLineComment.run();
        
        return candidates;
    }
    
    private SatdCandidateDTO createCandidate(String commentText, int startLineNum, int startIndex, int endIndex, List<SourceLine> hunkLines, String filename) {
        int precedingStart = Math.max(0, startIndex - 5);
        int precedingEnd = startIndex - 1;
        
        StringBuilder precedingCode = new StringBuilder();
        for (int i = precedingStart; i <= precedingEnd; i++) {
            precedingCode.append(hunkLines.get(i).content);
            if (i < precedingEnd) {
                precedingCode.append("\n");
            }
        }
        
        int succeedingStart = endIndex + 1;
        int succeedingEnd = Math.min(hunkLines.size() - 1, endIndex + 5);
        
        StringBuilder succeedingCode = new StringBuilder();
        for (int i = succeedingStart; i <= succeedingEnd; i++) {
            succeedingCode.append(hunkLines.get(i).content);
            if (i < succeedingEnd) {
                succeedingCode.append("\n");
            }
        }
        
        return SatdCandidateDTO.builder()
                .commentText(commentText.trim())
                .precedingCode(precedingCode.toString())
                .succeedingCode(succeedingCode.toString())
                .filename(filename)
                .language("Java")
                .lineNumber(startLineNum)
                .build();
    }
}
