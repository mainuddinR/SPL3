package com.mainuddin.codereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GithubPullRequestDTO {
    @Data
    public static class GitRef {
        private String sha;
    }

    private GitRef head;
    private GitRef base;
    private Long id;
    private Integer number;
    private String title;
    private String state;
    private String body;
    @JsonProperty("html_url")
    private String htmlUrl;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
