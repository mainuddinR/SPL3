package com.mainuddin.codereview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubBranchDTO {
    private String name;
    private CommitDTO commit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitDTO {
        private String sha;
        private String url;
    }
}
