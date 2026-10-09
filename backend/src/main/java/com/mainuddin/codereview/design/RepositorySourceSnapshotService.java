package com.mainuddin.codereview.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.entity.GithubProject;
import com.mainuddin.codereview.entity.PullRequestFile;
import com.mainuddin.codereview.security.GithubAuthorizedClientService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authenticated, bounded Git tree/blob snapshot; source is held only for the analysis job. */
@Service
public class RepositorySourceSnapshotService {
    @org.springframework.beans.factory.annotation.Value("${app.design.max-java-files:250}")
    private int MAX_JAVA_FILES = 250;
    @org.springframework.beans.factory.annotation.Value("${app.design.max-source-file-bytes:262144}")
    private int MAX_SOURCE_BYTES = 256 * 1024;
    @org.springframework.beans.factory.annotation.Value("${app.design.max-total-source-bytes:12582912}")
    private int MAX_TOTAL_BYTES = 12 * 1024 * 1024;
    @org.springframework.beans.factory.annotation.Value("${app.design.max-tree-response-bytes:8388608}")
    private int MAX_TREE_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_OTHER_RESPONSE_BYTES = 800 * 1024;
    private final RestTemplate http;
    private final GithubAuthorizedClientService clients;
    private final ObjectMapper json;

    @jakarta.annotation.PostConstruct
    void validateLimits() {
        if (MAX_JAVA_FILES < 1 || MAX_JAVA_FILES > 10000 || MAX_SOURCE_BYTES < 1 || MAX_SOURCE_BYTES > 4 * 1024 * 1024
                || MAX_TOTAL_BYTES < 1 || MAX_TOTAL_BYTES > 64 * 1024 * 1024
                || MAX_TREE_RESPONSE_BYTES < 1 || MAX_TREE_RESPONSE_BYTES > 32 * 1024 * 1024)
            throw new IllegalArgumentException("Design source bounds must be positive and within the supported memory limits.");
    }

    public RepositorySourceSnapshotService(@Qualifier("githubSyncRestTemplate") RestTemplate http,
            GithubAuthorizedClientService clients, ObjectMapper json) {
        this.http = http; this.clients = clients; this.json = json;
    }

    public record Head(String sha, String defaultBranch) {}
    public record Snapshot(String sha, String defaultBranch, Map<String, String> sources) {}
    private record Blob(String path, String sha, int size) {}

    public Head exactCommit(GithubProject project, String githubId, String sha) {
        if (!validSha(sha)) throw unavailable();
        JsonNode commit = get(project, githubId, "/commits/" + sha);
        if (!sha.equalsIgnoreCase(commit.path("sha").asText())) throw unavailable();
        return new Head(sha.toLowerCase(java.util.Locale.ROOT), null);
    }

    public Head currentHead(GithubProject project, String githubId) {
        JsonNode metadata = get(project, githubId, "");
        String branch = metadata.path("default_branch").asText("");
        if (branch.isBlank() || branch.length() > 100 || branch.contains("..")) throw unavailable();
        JsonNode commit = get(project, githubId, "/commits/" + encodeSegment(branch));
        String sha = commit.path("sha").asText("");
        if (!validSha(sha)) throw unavailable();
        return new Head(sha, branch);
    }

    public Snapshot capture(GithubProject project, String githubId, Head head) {
        return capture(project, githubId, head, (done, total) -> {});
    }

    public Snapshot capture(GithubProject project, String githubId, Head head,
            java.util.function.BiConsumer<Integer, Integer> progress) {
        JsonNode tree = get(project, githubId, "/git/trees/" + head.sha() + "?recursive=1");
        if (tree.path("truncated").asBoolean(true) || !tree.path("tree").isArray())
            throw new DesignSourceException("TREE_TRUNCATED", "GitHub returned an incomplete repository tree. A complete design profile cannot be created safely.", null, null);
        List<Blob> blobs = new ArrayList<>();
        for (JsonNode item : tree.path("tree")) {
            String path = item.path("path").asText("");
            if (!"blob".equals(item.path("type").asText()) || !allowedJavaPath(path)) continue;
            String sha = item.path("sha").asText("");
            long size = item.path("size").asLong(Long.MAX_VALUE);
            if (!validSha(sha)) throw unavailable();
            if (size < 0 || size > MAX_SOURCE_BYTES) throw new DesignSourceException("JAVA_FILE_TOO_LARGE",
                    "A Java source file exceeds the safe file-size limit.", size, (long) MAX_SOURCE_BYTES);
            blobs.add(new Blob(path, sha, (int) size));
        }
        if (blobs.size() > MAX_JAVA_FILES) throw new DesignSourceException("TOO_MANY_JAVA_FILES",
                "Repository exceeds the safe Java-file count limit.", (long) blobs.size(), (long) MAX_JAVA_FILES);
        blobs.sort(Comparator.comparing(Blob::path));
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 0;
        int completed = 0;
        progress.accept(0, blobs.size());
        for (Blob blob : blobs) {
            total += blob.size();
            if (total > MAX_TOTAL_BYTES) throw new DesignSourceException("TOTAL_SOURCE_TOO_LARGE",
                    "Repository exceeds the safe total Java-source size limit.", (long) total, (long) MAX_TOTAL_BYTES);
            String source = source(project, githubId, blob.sha());
            if (source == null) throw new DesignSourceException("SOURCE_BLOB_INVALID",
                    "A Java source blob could not be verified and decoded safely.", null, null);
            sources.put(blob.path(), source);
            progress.accept(++completed, blobs.size());
        }
        return new Snapshot(head.sha(), head.defaultBranch(), Map.copyOf(sources));
    }

    public Map<String, String> changedJavaSources(GithubProject project, String githubId,
            List<PullRequestFile> files) {
        if (files.size() > 50) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Too many changed files for one design-conformance check.");
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 0;
        for (PullRequestFile file : files) {
            if (!allowedJavaPath(file.getFilename()) || "removed".equalsIgnoreCase(file.getStatus())) continue;
            if (!validSha(file.getSha())) throw new DesignSourceException("SOURCE_BLOB_INVALID", "A changed Java file has no verified source blob.", null, null);
            String source = source(project, githubId, file.getSha());
            if (source == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A changed Java source blob could not be decoded for design conformance.");
            total += source.getBytes(StandardCharsets.UTF_8).length;
            if (total > MAX_TOTAL_BYTES) throw new DesignSourceException("TOTAL_SOURCE_TOO_LARGE", "Changed Java source exceeds the safe analysis size limit.", (long) total, (long) MAX_TOTAL_BYTES);
            sources.put(file.getFilename(), source);
        }
        return Map.copyOf(sources);
    }

    /** Only the changed paths at the exact PR base; does not detect or classify the repository again. */
    public Map<String, String> changedJavaBaseSources(GithubProject project, String githubId,
            List<PullRequestFile> files, String baseSha) {
        if (!validSha(baseSha) || files.size() > 50) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "The PR base revision cannot be checked safely.");
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 0;
        for (PullRequestFile file : files) {
            if (!allowedJavaPath(file.getFilename()) || "added".equalsIgnoreCase(file.getStatus())
                    || "renamed".equalsIgnoreCase(file.getStatus())) continue;
            String path = java.util.Arrays.stream(file.getFilename().split("/")).map(RepositorySourceSnapshotService::encodeSegment)
                    .collect(java.util.stream.Collectors.joining("/"));
            JsonNode metadata = get(project, githubId, "/contents/" + path + "?ref=" + baseSha);
            String sha = metadata.path("sha").asText("");
            if (!"file".equals(metadata.path("type").asText()) || !validSha(sha)) throw unavailable();
            String text = source(project, githubId, sha);
            if (text == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A changed Java base blob could not be verified safely.");
            total += text.getBytes(StandardCharsets.UTF_8).length;
            if (total > MAX_TOTAL_BYTES) throw new DesignSourceException("TOTAL_SOURCE_TOO_LARGE", "Changed base source exceeds the safe analysis size limit.", (long) total, (long) MAX_TOTAL_BYTES);
            sources.put(file.getFilename(), text);
        }
        return Map.copyOf(sources);
    }

    private String source(GithubProject project, String githubId, String sha) {
        JsonNode blob = get(project, githubId, "/git/blobs/" + sha);
        if (!sha.equalsIgnoreCase(blob.path("sha").asText())
                || !"base64".equals(blob.path("encoding").asText())
                || blob.path("size").asLong(Long.MAX_VALUE) > MAX_SOURCE_BYTES) return null;
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(blob.path("content").asText(""));
            if (bytes.length > MAX_SOURCE_BYTES) return null;
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException invalid) {
            return null;
        }
    }

    private JsonNode get(GithubProject project, String githubId, String suffix) {
        var client = clients.loadValidForGithubId(githubId);
        if (client == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Reconnect GitHub before analyzing repository design.");
        if (!project.getOwnerLogin().matches("[A-Za-z0-9-]{1,39}")
                || !project.getName().matches("[A-Za-z0-9_.-]{1,100}")) throw unavailable();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(client.getAccessToken().getTokenValue());
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        String url = "https://api.github.com/repos/" + project.getOwnerLogin() + "/" + project.getName() + suffix;
        try {
            int limit = suffix.startsWith("/git/trees/") ? MAX_TREE_RESPONSE_BYTES : MAX_OTHER_RESPONSE_BYTES;
            JsonNode result = http.execute(java.net.URI.create(url), HttpMethod.GET,
                    request -> request.getHeaders().putAll(headers), response -> {
                        byte[] body = response.getBody().readNBytes(limit + 1);
                        if (body.length > limit) throw new DesignSourceException(suffix.startsWith("/git/trees/")
                                ? "TREE_RESPONSE_TOO_LARGE" : "SOURCE_BLOB_INVALID",
                                "GitHub source metadata exceeds the safe response-size limit.", (long) body.length, (long) limit);
                        return json.readTree(body);
                    });
            if (result == null) throw unavailable();
            return result;
        } catch (HttpClientErrorException failure) {
            if (failure.getStatusCode().value() == 401)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Reconnect GitHub before analyzing repository design.");
            if (failure.getStatusCode().value() == 429 || failure.getStatusCode().value() == 403
                    && "0".equals(failure.getResponseHeaders() == null ? null
                    : failure.getResponseHeaders().getFirst("X-RateLimit-Remaining")))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "GitHub request limit reached. Retry later.");
            throw unavailable();
        } catch (RestClientException failure) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub source snapshot is unavailable right now.");
    }

    private static String encodeSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean validSha(String sha) { return sha != null && sha.matches("[0-9a-fA-F]{40}"); }
    static boolean allowedJavaPath(String path) {
        if (path == null || path.startsWith("/") || path.contains("\\") || !path.endsWith(".java")) return false;
        if (path.contains("/test/") || path.startsWith("test/") || path.contains("/tests/") || path.startsWith("tests/")) return false;
        for (String part : path.split("/")) {
            if (part.isBlank() || part.equals(".") || part.equals("..") ||
                    List.of("target", "build", "generated", "vendor", "node_modules", ".git").contains(part)) return false;
        }
        return true;
    }
}
