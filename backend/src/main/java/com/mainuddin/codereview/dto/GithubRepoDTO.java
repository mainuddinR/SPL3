package com.mainuddin.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepoDTO {
    private Long id;
    private String name;
    
    @JsonProperty("full_name")
    private String fullName;
    
    @JsonProperty("html_url")
    private String htmlUrl;
    
    private String description;
    private String language;
    
    @JsonProperty("stargazers_count")
    private Integer stargazersCount;
    
    private OwnerDTO owner;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerDTO {
        private String login;
        
        @JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
