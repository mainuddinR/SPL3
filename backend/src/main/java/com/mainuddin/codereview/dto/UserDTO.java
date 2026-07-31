package com.mainuddin.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private String githubId;
    private String username;
    private String email;
    private String avatarUrl;
    private String role;
}
