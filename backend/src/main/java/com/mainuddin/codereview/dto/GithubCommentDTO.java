package com.mainuddin.codereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GithubCommentDTO {
    private Long id;
    private String path;
    private Integer position;
    private String body;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private UserDTO user;

    @Data
    public static class UserDTO {
        private String login;
    }
}
