package com.mainuddin.codereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GithubUserSearchDTO(List<UserResult> users, int page, boolean hasNext) {
    public record UserResult(String login, @JsonProperty("avatar_url") String avatarUrl,
                             @JsonProperty("html_url") String htmlUrl, String name) {}
}
