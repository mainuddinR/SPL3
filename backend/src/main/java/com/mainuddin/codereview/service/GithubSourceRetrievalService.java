package com.mainuddin.codereview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.dto.SatdCandidateDTO;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.PullRequestFile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class GithubSourceRetrievalService {
    public static final int MAX_SOURCE_BYTES = 512 * 1024;
    private static final int MAX_RESPONSE_BYTES = 800 * 1024;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GithubSourceRetrievalService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Session newSession() {
        return new Session();
    }

    public final class Session {
        private final Map<Key, VerifiedSourceResult> cache = new HashMap<>();

        public VerifiedSourceResult retrieve(GithubProject project, String headSha, PullRequestFile file) {
            if (project == null || headSha == null || file == null || file.getSha() == null ||
                    file.getFilename() == null) return VerifiedSourceResult.of(VerifiedSourceResult.Status.UNAVAILABLE);
            Key key = new Key(project.getGithubRepoId(), headSha, file.getFilename(), file.getSha());
            return cache.computeIfAbsent(key, ignored -> fetch(project, headSha, file));
        }

        public VerifiedSourceResult forCandidate(GithubProject project, String headSha, PullRequestFile file,
                                                  SatdCandidateDTO candidate) {
            VerifiedSourceResult result = retrieve(project, headSha, file);
            if (result.status() != VerifiedSourceResult.Status.VERIFIED) return result;
            if (candidate == null || !file.getFilename().equals(candidate.getFilename()) ||
                    !matchesCandidate(result.source(), candidate)) {
                return VerifiedSourceResult.of(VerifiedSourceResult.Status.LOCATION_MISMATCH);
            }
            return result;
        }
    }

    private record Key(Long repositoryId, String headSha, String path, String blobSha) {}

    private VerifiedSourceResult fetch(GithubProject project, String headSha, PullRequestFile file) {
        String path = file.getFilename();
        if (!headSha.matches("[0-9a-fA-F]{40}") || !file.getSha().matches("[0-9a-fA-F]{40}") ||
                !path.endsWith(".java") || path.isBlank() || path.startsWith("/") || path.contains("\\") ||
                java.util.Arrays.stream(path.split("/", -1)).anyMatch(part -> part.isBlank() || part.equals(".") || part.equals(".."))) {
            return VerifiedSourceResult.of(VerifiedSourceResult.Status.UNAVAILABLE);
        }
        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.github.com")
                .pathSegment("repos", project.getOwnerLogin(), project.getName(), "contents")
                .pathSegment(path.split("/"))
                .queryParam("ref", headSha).build().encode().toUri();
        try {
            return restTemplate.execute(uri, HttpMethod.GET,
                    request -> request.getHeaders().set("Accept", "application/vnd.github+json"),
                    response -> {
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) {
                            return VerifiedSourceResult.of(VerifiedSourceResult.Status.UNSUPPORTED);
                        }
                        JsonNode json = objectMapper.readTree(body);
                        if (!"file".equals(json.path("type").asText()) ||
                                !"base64".equals(json.path("encoding").asText()) ||
                                json.path("size").asLong(Long.MAX_VALUE) > MAX_SOURCE_BYTES) {
                            return VerifiedSourceResult.of(VerifiedSourceResult.Status.UNSUPPORTED);
                        }
                        if (!file.getSha().equalsIgnoreCase(json.path("sha").asText())) {
                            return VerifiedSourceResult.of(VerifiedSourceResult.Status.SHA_MISMATCH);
                        }
                        byte[] decoded;
                        try {
                            decoded = Base64.getMimeDecoder().decode(json.path("content").asText());
                        } catch (IllegalArgumentException invalid) {
                            return VerifiedSourceResult.of(VerifiedSourceResult.Status.UNSUPPORTED);
                        }
                        if (decoded.length > MAX_SOURCE_BYTES) {
                            return VerifiedSourceResult.of(VerifiedSourceResult.Status.UNSUPPORTED);
                        }
                        try {
                            String source = StandardCharsets.UTF_8.newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                                    .decode(ByteBuffer.wrap(decoded)).toString();
                            return new VerifiedSourceResult(VerifiedSourceResult.Status.VERIFIED, source);
                        } catch (CharacterCodingException invalid) {
                            return VerifiedSourceResult.of(VerifiedSourceResult.Status.UNSUPPORTED);
                        }
                    });
        } catch (RestClientResponseException apiFailure) {
            return VerifiedSourceResult.of(apiFailure.getStatusCode().value() == 404
                    ? VerifiedSourceResult.Status.UNAVAILABLE : VerifiedSourceResult.Status.API_FAILURE);
        } catch (Exception failure) {
            return VerifiedSourceResult.of(VerifiedSourceResult.Status.API_FAILURE);
        }
    }

    public static boolean matchesCandidate(String source, SatdCandidateDTO candidate) {
        if (source == null || candidate == null || candidate.getLineNumber() == null ||
                candidate.getCommentText() == null || candidate.getCommentText().isBlank()) return false;
        String[] sourceLines = source.split("\\R", -1);
        String[] commentLines = candidate.getCommentText().split("\\R", -1);
        int start = candidate.getLineNumber() - 1;
        if (start < 0 || start + commentLines.length > sourceLines.length) return false;
        for (int i = 0; i < commentLines.length; i++) {
            String expected = commentLines[i].trim();
            if (expected.isEmpty() || !sourceLines[start + i].contains(expected)) return false;
        }
        return true;
    }
}
